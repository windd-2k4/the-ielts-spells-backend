package com.theieltsspells.progress.application;

import com.theieltsspells.progress.application.dto.CuratedVocabularyDeckDetailResponse;
import com.theieltsspells.progress.application.dto.CuratedVocabularyDeckSummaryResponse;
import com.theieltsspells.progress.application.dto.CuratedVocabularyItemResponse;
import com.theieltsspells.progress.application.dto.CuratedVocabularySessionResponse;
import com.theieltsspells.progress.application.dto.UpdateCuratedVocabularyProgressRequest;
import com.theieltsspells.progress.application.dto.VocabularyNotebookResponse;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CuratedVocabularyApplicationService {

    private static final int DEFAULT_SESSION_SIZE = 10;
    private static final String DEFAULT_PERSONAL_TOPIC = "Từ vựng IELTS Pick";

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<CuratedVocabularyDeckSummaryResponse> getDecks(UUID studentId) {
        return jdbc.query("""
                select d.id, d.slug, d.name, d.category, d.description, d.word_count, d.order_index,
                       count(p.id) filter (where p.mastery_status = 'FAMILIAR')::int as familiar_count,
                       count(p.id) filter (where p.mastery_status = 'MASTERED')::int as mastered_count
                from public.curated_vocabulary_decks d
                left join public.student_curated_vocabulary_progress p
                       on p.deck_id = d.id and p.student_id = ?
                group by d.id, d.slug, d.name, d.category, d.description, d.word_count, d.order_index
                order by d.order_index, d.name
                """, (rs, row) -> {
            int total = rs.getInt("word_count");
            int mastered = rs.getInt("mastered_count");
            int familiar = rs.getInt("familiar_count");
            int notLearned = Math.max(0, total - mastered - familiar);
            return new CuratedVocabularyDeckSummaryResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("description"),
                    total,
                    notLearned,
                    familiar,
                    mastered,
                    rs.getInt("order_index")
            );
        }, studentId);
    }

    @Transactional(readOnly = true)
    public CuratedVocabularyDeckDetailResponse getDeckDetail(UUID studentId, UUID deckId) {
        CuratedVocabularyDeckSummaryResponse deck = getDeckSummary(studentId, deckId);
        List<CuratedVocabularyItemResponse> items = jdbc.query("""
                select i.id, i.deck_id, i.term, i.part_of_speech, i.phonetic, i.meaning,
                       i.example_sentence, i.example_sentence_vi, i.chunk,
                       i.collocations::text as collocations_json,
                       i.exercise_json::text as exercise_json,
                       i.tip, i.drills_json::text as drills_json, i.order_index,
                       coalesce(p.mastery_status, 'NOT_LEARNED') as mastery_status,
                       coalesce(p.review_count, 0) as review_count,
                       p.last_reviewed_at,
                       exists (
                         select 1 from public.student_vocabulary sv
                         where sv.student_id = ? and lower(sv.term) = lower(i.term)
                       ) as saved_in_notebook
                from public.curated_vocabulary_items i
                left join public.student_curated_vocabulary_progress p
                       on p.item_id = i.id and p.student_id = ?
                where i.deck_id = ?
                order by i.order_index, i.id
                """, (rs, row) -> mapItemResponse(rs), studentId, studentId, deckId);

        return new CuratedVocabularyDeckDetailResponse(deck, items);
    }

    @Transactional(readOnly = true)
    public CuratedVocabularySessionResponse startStudySession(UUID studentId, UUID deckId) {
        CuratedVocabularyDeckSummaryResponse deck = getDeckSummary(studentId, deckId);
        List<CuratedVocabularyItemResponse> allItems = getDeckDetail(studentId, deckId).items();

        if (allItems.isEmpty()) {
            return new CuratedVocabularySessionResponse(deckId, deck.name(), 0, List.of());
        }

        List<CuratedVocabularyItemResponse> notLearned = new ArrayList<>();
        List<CuratedVocabularyItemResponse> familiar = new ArrayList<>();
        List<CuratedVocabularyItemResponse> mastered = new ArrayList<>();

        for (CuratedVocabularyItemResponse item : allItems) {
            switch (item.masteryStatus()) {
                case "MASTERED" -> mastered.add(item);
                case "FAMILIAR" -> familiar.add(item);
                default -> notLearned.add(item);
            }
        }

        List<CuratedVocabularyItemResponse> sessionItems = new ArrayList<>();

        // Priority 1: Take up to 7 new/unlearned words
        int freshTarget = Math.min(7, notLearned.size());
        for (int i = 0; i < freshTarget; i++) {
            sessionItems.add(notLearned.get(i));
        }

        // Priority 2: Take review/familiar words to reach 10
        int reviewNeeded = DEFAULT_SESSION_SIZE - sessionItems.size();
        int familiarToTake = Math.min(reviewNeeded, familiar.size());
        for (int i = 0; i < familiarToTake; i++) {
            sessionItems.add(familiar.get(i));
        }

        // Priority 3: If still under 10, fill with remaining notLearned
        if (sessionItems.size() < DEFAULT_SESSION_SIZE && notLearned.size() > freshTarget) {
            int remainingNeeded = DEFAULT_SESSION_SIZE - sessionItems.size();
            int remainingToTake = Math.min(remainingNeeded, notLearned.size() - freshTarget);
            for (int i = 0; i < remainingToTake; i++) {
                sessionItems.add(notLearned.get(freshTarget + i));
            }
        }

        // Priority 4: If still under 10, fill with mastered words
        if (sessionItems.size() < DEFAULT_SESSION_SIZE && !mastered.isEmpty()) {
            int remainingNeeded = DEFAULT_SESSION_SIZE - sessionItems.size();
            int masteredToTake = Math.min(remainingNeeded, mastered.size());
            for (int i = 0; i < masteredToTake; i++) {
                sessionItems.add(mastered.get(i));
            }
        }

        return new CuratedVocabularySessionResponse(deckId, deck.name(), sessionItems.size(), sessionItems);
    }

    @Transactional
    public CuratedVocabularyItemResponse updateProgress(
            UUID studentId, UUID deckId, UUID itemId, UpdateCuratedVocabularyProgressRequest request) {
        requireDeck(deckId);
        requireItem(deckId, itemId);

        jdbc.update("""
                insert into public.student_curated_vocabulary_progress
                  (student_id, deck_id, item_id, mastery_status, review_count, last_reviewed_at, created_at, updated_at)
                values (?, ?, ?, ?, 1, now(), now(), now())
                on conflict (student_id, item_id) do update
                set mastery_status = excluded.mastery_status,
                    review_count = student_curated_vocabulary_progress.review_count + 1,
                    last_reviewed_at = now(),
                    updated_at = now()
                """, studentId, deckId, itemId, request.masteryStatus());

        return jdbc.queryForObject("""
                select i.id, i.deck_id, i.term, i.part_of_speech, i.phonetic, i.meaning,
                       i.example_sentence, i.example_sentence_vi, i.chunk,
                       i.collocations::text as collocations_json,
                       i.exercise_json::text as exercise_json,
                       i.tip, i.drills_json::text as drills_json, i.order_index,
                       p.mastery_status, p.review_count, p.last_reviewed_at,
                       exists (
                         select 1 from public.student_vocabulary sv
                         where sv.student_id = ? and lower(sv.term) = lower(i.term)
                       ) as saved_in_notebook
                from public.curated_vocabulary_items i
                join public.student_curated_vocabulary_progress p
                  on p.item_id = i.id and p.student_id = ?
                where i.id = ? and i.deck_id = ?
                """, (rs, row) -> mapItemResponse(rs), studentId, studentId, itemId, deckId);
    }

    @Transactional
    public VocabularyNotebookResponse.Card saveToPersonalNotebook(UUID studentId, UUID itemId) {
        CuratedVocabularyItemResponse item = jdbc.queryForObject("""
                select i.id, i.deck_id, i.term, i.part_of_speech, i.phonetic, i.meaning,
                       i.example_sentence, i.example_sentence_vi, i.chunk,
                       i.collocations::text as collocations_json,
                       i.exercise_json::text as exercise_json,
                       i.tip, i.drills_json::text as drills_json, i.order_index,
                       'NOT_LEARNED' as mastery_status, 0 as review_count, null as last_reviewed_at,
                       true as saved_in_notebook
                from public.curated_vocabulary_items i
                where i.id = ?
                """, (rs, row) -> mapItemResponse(rs), itemId);

        if (item == null) {
            throw new ResourceNotFoundException("Curated vocabulary item not found: " + itemId);
        }

        UUID topicId = ensureCuratedTopic(studentId);

        return jdbc.queryForObject("""
                insert into public.student_vocabulary
                  (student_id, topic_id, term, meaning, example_sentence, part_of_speech, note, source_label,
                   mastery_level, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'YouPass Pick', 0, now(), now())
                on conflict (student_id, topic_id, (lower(term))) do update
                set meaning = coalesce(nullif(excluded.meaning, ''), student_vocabulary.meaning),
                    example_sentence = coalesce(nullif(excluded.example_sentence, ''), student_vocabulary.example_sentence),
                    part_of_speech = coalesce(nullif(excluded.part_of_speech, ''), student_vocabulary.part_of_speech),
                    note = coalesce(nullif(excluded.note, ''), student_vocabulary.note),
                    updated_at = now()
                returning id, topic_id, term, meaning, example_sentence, part_of_speech, note,
                          source_label, mastery_level, created_at, last_reviewed_at
                """, (rs, row) -> new VocabularyNotebookResponse.Card(
                rs.getObject("id", UUID.class),
                rs.getObject("topic_id", UUID.class),
                rs.getString("term"),
                rs.getString("meaning"),
                rs.getString("example_sentence"),
                rs.getString("part_of_speech"),
                rs.getString("note"),
                rs.getString("source_label"),
                "NOT_LEARNED",
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("last_reviewed_at", OffsetDateTime.class)),
                studentId, topicId, item.term(), item.meaning(), item.exampleSentence(), item.partOfSpeech(), item.phonetic());
    }

    private CuratedVocabularyDeckSummaryResponse getDeckSummary(UUID studentId, UUID deckId) {
        CuratedVocabularyDeckSummaryResponse summary = jdbc.queryForObject("""
                select d.id, d.slug, d.name, d.category, d.description, d.word_count, d.order_index,
                       count(p.id) filter (where p.mastery_status = 'FAMILIAR')::int as familiar_count,
                       count(p.id) filter (where p.mastery_status = 'MASTERED')::int as mastered_count
                from public.curated_vocabulary_decks d
                left join public.student_curated_vocabulary_progress p
                       on p.deck_id = d.id and p.student_id = ?
                where d.id = ?
                group by d.id, d.slug, d.name, d.category, d.description, d.word_count, d.order_index
                """, (rs, row) -> {
            int total = rs.getInt("word_count");
            int mastered = rs.getInt("mastered_count");
            int familiar = rs.getInt("familiar_count");
            int notLearned = Math.max(0, total - mastered - familiar);
            return new CuratedVocabularyDeckSummaryResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getString("description"),
                    total,
                    notLearned,
                    familiar,
                    mastered,
                    rs.getInt("order_index")
            );
        }, studentId, deckId);

        if (summary == null) {
            throw new ResourceNotFoundException("Curated vocabulary deck not found: " + deckId);
        }
        return summary;
    }

    private UUID ensureCuratedTopic(UUID studentId) {
        try {
            return jdbc.queryForObject("""
                    insert into public.student_vocabulary_topics (student_id, name)
                    values (?, ?)
                    returning id
                    """, UUID.class, studentId, DEFAULT_PERSONAL_TOPIC);
        } catch (DuplicateKeyException duplicate) {
            return jdbc.queryForObject("""
                    select id from public.student_vocabulary_topics
                    where student_id = ? and lower(name) = lower(?)
                    """, UUID.class, studentId, DEFAULT_PERSONAL_TOPIC);
        }
    }

    private void requireDeck(UUID deckId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from public.curated_vocabulary_decks where id = ?", Integer.class, deckId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Curated vocabulary deck not found: " + deckId);
        }
    }

    private void requireItem(UUID deckId, UUID itemId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from public.curated_vocabulary_items where id = ? and deck_id = ?",
                Integer.class, itemId, deckId);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Item " + itemId + " not found in deck " + deckId);
        }
    }

    private CuratedVocabularyItemResponse mapItemResponse(ResultSet rs) throws SQLException {
        return new CuratedVocabularyItemResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("deck_id", UUID.class),
                rs.getString("term"),
                rs.getString("part_of_speech"),
                rs.getString("phonetic"),
                rs.getString("meaning"),
                rs.getString("example_sentence"),
                rs.getString("example_sentence_vi"),
                rs.getString("chunk"),
                rs.getString("collocations_json"),
                rs.getString("exercise_json"),
                rs.getString("tip"),
                rs.getString("drills_json"),
                rs.getInt("order_index"),
                rs.getString("mastery_status"),
                rs.getInt("review_count"),
                rs.getObject("last_reviewed_at", OffsetDateTime.class),
                rs.getBoolean("saved_in_notebook")
        );
    }
}
