package com.theieltsspells.progress.application;

import com.theieltsspells.progress.application.dto.CreateVocabularyTopicRequest;
import com.theieltsspells.progress.application.dto.SaveVocabularyCardRequest;
import com.theieltsspells.progress.application.dto.UpdateVocabularyMasteryRequest;
import com.theieltsspells.progress.application.dto.VocabularyMasteryStatus;
import com.theieltsspells.progress.application.dto.VocabularyNotebookResponse;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentVocabularyApplicationService {
    private static final String DEFAULT_TOPIC = "Từ Reading";

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public VocabularyNotebookResponse notebook(UUID studentId) {
        List<VocabularyNotebookResponse.Card> cards = jdbc.query("""
                select id, topic_id, term, meaning, example_sentence, part_of_speech, note,
                       source_label, mastery_level, created_at, last_reviewed_at
                from public.student_vocabulary
                where student_id = ?
                order by created_at desc, id
                """, (rs, row) -> card(rs), studentId);

        List<VocabularyNotebookResponse.Topic> topics = jdbc.query("""
                select topic.id, topic.name, count(vocabulary.id)::int card_count,
                       count(vocabulary.id) filter (where vocabulary.mastery_level >= 5)::int mastered_count
                from public.student_vocabulary_topics topic
                left join public.student_vocabulary vocabulary on vocabulary.topic_id = topic.id
                where topic.student_id = ?
                group by topic.id, topic.name, topic.created_at
                order by topic.created_at, topic.name
                """, (rs, row) -> new VocabularyNotebookResponse.Topic(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getInt("card_count"), rs.getInt("mastered_count")), studentId);

        int familiar = (int) cards.stream().filter(card -> "FAMILIAR".equals(card.masteryStatus())).count();
        int mastered = (int) cards.stream().filter(card -> "MASTERED".equals(card.masteryStatus())).count();
        return new VocabularyNotebookResponse(
                new VocabularyNotebookResponse.Summary(cards.size(), cards.size() - familiar - mastered, familiar, mastered),
                topics,
                cards);
    }

    @Transactional
    public VocabularyNotebookResponse.Topic createTopic(UUID studentId, CreateVocabularyTopicRequest request) {
        String name = clean(request.name());
        UUID id;
        try {
            id = jdbc.queryForObject("""
                    insert into public.student_vocabulary_topics (student_id, name)
                    values (?, ?)
                    returning id
                    """, UUID.class, studentId, name);
        } catch (DuplicateKeyException duplicate) {
            id = jdbc.queryForObject("""
                    select id from public.student_vocabulary_topics
                    where student_id = ? and lower(name) = lower(?)
                    """, UUID.class, studentId, name);
        }
        return new VocabularyNotebookResponse.Topic(id, name, 0, 0);
    }

    @Transactional
    public VocabularyNotebookResponse.Card addCard(UUID studentId, SaveVocabularyCardRequest request) {
        UUID topicId = request.topicId() == null ? ensureDefaultTopic(studentId) : request.topicId();
        requireTopic(studentId, topicId);
        return jdbc.queryForObject("""
                insert into public.student_vocabulary
                  (student_id, topic_id, term, meaning, example_sentence, part_of_speech, note, source_label,
                   mastery_level, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 0, now(), now())
                on conflict (student_id, topic_id, (lower(term))) do update
                set meaning = coalesce(nullif(excluded.meaning, ''), student_vocabulary.meaning),
                    example_sentence = coalesce(nullif(excluded.example_sentence, ''), student_vocabulary.example_sentence),
                    part_of_speech = coalesce(nullif(excluded.part_of_speech, ''), student_vocabulary.part_of_speech),
                    note = coalesce(nullif(excluded.note, ''), student_vocabulary.note),
                    source_label = coalesce(nullif(excluded.source_label, ''), student_vocabulary.source_label),
                    updated_at = now()
                returning id, topic_id, term, meaning, example_sentence, part_of_speech, note,
                          source_label, mastery_level, created_at, last_reviewed_at
                """, (rs, row) -> card(rs), studentId, topicId, clean(request.term()),
                nullable(request.meaning()), nullable(request.exampleSentence()), nullable(request.partOfSpeech()),
                nullable(request.note()), nullable(request.sourceLabel()));
    }

    @Transactional
    public VocabularyNotebookResponse.Card updateCard(UUID studentId, UUID cardId, SaveVocabularyCardRequest request) {
        UUID topicId = request.topicId() == null ? requireCardTopic(studentId, cardId) : request.topicId();
        requireTopic(studentId, topicId);
        try {
            List<VocabularyNotebookResponse.Card> updated = jdbc.query("""
                    update public.student_vocabulary
                    set topic_id = ?, term = ?, meaning = ?, example_sentence = ?, part_of_speech = ?,
                        note = ?, source_label = ?, updated_at = now()
                    where id = ? and student_id = ?
                    returning id, topic_id, term, meaning, example_sentence, part_of_speech, note,
                              source_label, mastery_level, created_at, last_reviewed_at
                    """, (rs, row) -> card(rs), topicId, clean(request.term()), nullable(request.meaning()),
                    nullable(request.exampleSentence()), nullable(request.partOfSpeech()), nullable(request.note()),
                    nullable(request.sourceLabel()), cardId, studentId);
            if (updated.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy từ vựng.");
            return updated.getFirst();
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessRuleException("Từ này đã có trong chủ đề đã chọn.");
        }
    }

    @Transactional
    public VocabularyNotebookResponse.Card updateMastery(
            UUID studentId, UUID cardId, UpdateVocabularyMasteryRequest request) {
        List<VocabularyNotebookResponse.Card> updated = jdbc.query("""
                update public.student_vocabulary
                set mastery_level = ?, last_reviewed_at = now(), updated_at = now()
                where id = ? and student_id = ?
                returning id, topic_id, term, meaning, example_sentence, part_of_speech, note,
                          source_label, mastery_level, created_at, last_reviewed_at
                """, (rs, row) -> card(rs), request.status().level(), cardId, studentId);
        if (updated.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy từ vựng.");
        return updated.getFirst();
    }

    @Transactional
    public void deleteCard(UUID studentId, UUID cardId) {
        if (jdbc.update("delete from public.student_vocabulary where id = ? and student_id = ?", cardId, studentId) == 0) {
            throw new ResourceNotFoundException("Không tìm thấy từ vựng.");
        }
    }

    private UUID ensureDefaultTopic(UUID studentId) {
        List<UUID> existing = jdbc.query("""
                select id from public.student_vocabulary_topics
                where student_id = ? and lower(name) = lower(?)
                """, (rs, row) -> rs.getObject("id", UUID.class), studentId, DEFAULT_TOPIC);
        if (!existing.isEmpty()) return existing.getFirst();
        return jdbc.queryForObject("""
                insert into public.student_vocabulary_topics (student_id, name)
                values (?, ?) returning id
                """, UUID.class, studentId, DEFAULT_TOPIC);
    }

    private void requireTopic(UUID studentId, UUID topicId) {
        Boolean exists = jdbc.queryForObject("""
                select exists(select 1 from public.student_vocabulary_topics where id = ? and student_id = ?)
                """, Boolean.class, topicId, studentId);
        if (!Boolean.TRUE.equals(exists)) throw new ResourceNotFoundException("Không tìm thấy chủ đề từ vựng.");
    }

    private UUID requireCardTopic(UUID studentId, UUID cardId) {
        List<UUID> topics = jdbc.query("""
                select topic_id from public.student_vocabulary where id = ? and student_id = ?
                """, (rs, row) -> rs.getObject("topic_id", UUID.class), cardId, studentId);
        if (topics.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy từ vựng.");
        return topics.getFirst();
    }

    private VocabularyNotebookResponse.Card card(ResultSet rs) throws SQLException {
        short mastery = rs.getShort("mastery_level");
        return new VocabularyNotebookResponse.Card(
                rs.getObject("id", UUID.class), rs.getObject("topic_id", UUID.class),
                rs.getString("term"), rs.getString("meaning"), rs.getString("example_sentence"),
                rs.getString("part_of_speech"), rs.getString("note"), rs.getString("source_label"),
                VocabularyMasteryStatus.fromLevel(mastery).name(),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("last_reviewed_at", OffsetDateTime.class));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String nullable(String value) {
        String cleaned = value == null ? null : value.trim();
        return cleaned == null || cleaned.isEmpty() ? null : cleaned;
    }
}

