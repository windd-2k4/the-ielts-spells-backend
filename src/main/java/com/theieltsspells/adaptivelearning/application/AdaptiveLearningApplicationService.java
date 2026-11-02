package com.theieltsspells.adaptivelearning.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveLearnerProfileResponse;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveContentMappingsResponse;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveKnowledgeGraphResponse;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveLearningOverviewResponse;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveRecommendationResponse;
import com.theieltsspells.adaptivelearning.application.dto.UpsertAdaptiveContentMappingRequest;
import com.theieltsspells.adaptivelearning.domain.MasteryUpdatePolicy;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ReadingAttemptGradedEvent;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdaptiveLearningApplicationService {
    private static final BigDecimal INITIAL_MASTERY = new BigDecimal("0.5000");
    private static final String MODEL_VERSION = "weighted-average-v1";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Value("${app.adaptive-learning.enabled:false}")
    private boolean enabled;

    // ponytail: in-process after-commit delivery keeps submissions isolated;
    // add an outbox only when adaptive evidence must survive process crashes.
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(ReadingAttemptGradedEvent event) {
        if (!enabled || !"READING".equals(event.skill())) {
            return;
        }
        int inserted = jdbc.update("""
                insert into public.adaptive_learning_events(
                  id, source_event_id, event_type, learner_id, attempt_id, occurred_at, payload
                ) values (?, ?, 'READING_ATTEMPT_GRADED', ?, ?, ?, cast(? as jsonb))
                on conflict (source_event_id) do nothing
                """, UUID.randomUUID(), event.attemptId(), event.learnerId(), event.attemptId(),
                event.submittedAt(), json(event));
        if (inserted == 0) {
            return;
        }

        Map<String, String> explicitMappings = explicitMappings(event.testVersionId());
        Map<String, String> typeMappings = typeMappings();
        for (var evidence : event.evidence()) {
            if (!evidence.answered() || evidence.correct() == null) {
                continue;
            }
            String componentCode = explicitMappings.get(evidence.questionKey());
            if (componentCode == null) {
                componentCode = typeMappings.get(evidence.questionType());
            }
            if (componentCode != null) {
                updateState(event, componentCode, evidence.correct());
            }
        }
        createRecommendation(event);
        jdbc.update("""
                update public.adaptive_learning_events set processed_at = now()
                where source_event_id = ?
                """, event.attemptId());
    }

    public Optional<AdaptiveRecommendationResponse> currentRecommendation(UUID learnerId) {
        if (!enabled) {
            return Optional.empty();
        }
        return jdbc.query("""
                select recommendation.id, recommendation.component_code, component.name component_name,
                  recommendation.mastery_at_decision, recommendation.confidence_at_decision,
                  state.evidence_count, recommendation.title, recommendation.reason,
                  recommendation.duration_minutes, recommendation.difficulty,
                  recommendation.href, recommendation.created_at
                from public.adaptive_recommendations recommendation
                join public.adaptive_knowledge_components component
                  on component.code = recommendation.component_code
                join public.adaptive_learner_knowledge_states state
                  on state.learner_id = recommendation.learner_id
                  and state.component_code = recommendation.component_code
                where recommendation.learner_id = ? and recommendation.status = 'ACTIVE'
                order by recommendation.created_at desc limit 1
                """, (rs, ignored) -> new AdaptiveRecommendationResponse(
                rs.getObject("id", UUID.class), rs.getString("component_code"),
                rs.getString("component_name"), rs.getBigDecimal("mastery_at_decision"),
                rs.getBigDecimal("confidence_at_decision"), rs.getInt("evidence_count"),
                rs.getString("title"), rs.getString("reason"),
                rs.getObject("duration_minutes", Integer.class), rs.getString("difficulty"),
                rs.getString("href"), rs.getObject("created_at", java.time.OffsetDateTime.class)
        ), learnerId).stream().findFirst();
    }

    public AdaptiveLearnerProfileResponse learnerProfile(UUID learnerId) {
        List<AdaptiveLearnerProfileResponse.KnowledgeState> states = enabled ? jdbc.query("""
                select state.component_code, component.name component_name, component.skill::text skill,
                  state.mastery_probability, state.confidence, state.evidence_count, state.correct_count,
                  state.model_version, state.last_evidence_at
                from public.adaptive_learner_knowledge_states state
                join public.adaptive_knowledge_components component on component.code = state.component_code
                where state.learner_id = ?
                order by state.mastery_probability, component.name
                """, (rs, ignored) -> new AdaptiveLearnerProfileResponse.KnowledgeState(
                rs.getString("component_code"), rs.getString("component_name"), rs.getString("skill"),
                rs.getBigDecimal("mastery_probability"), rs.getBigDecimal("confidence"),
                rs.getInt("evidence_count"), rs.getInt("correct_count"), rs.getString("model_version"),
                rs.getObject("last_evidence_at", java.time.OffsetDateTime.class)
        ), learnerId) : List.of();
        List<AdaptiveLearnerProfileResponse.Recommendation> recommendations = enabled ? jdbc.query("""
                select id, component_code, title, reason, status, source_attempt_id, model_version, created_at
                from public.adaptive_recommendations where learner_id = ?
                order by created_at desc limit 20
                """, (rs, ignored) -> new AdaptiveLearnerProfileResponse.Recommendation(
                rs.getObject("id", UUID.class), rs.getString("component_code"), rs.getString("title"),
                rs.getString("reason"), rs.getString("status"),
                rs.getObject("source_attempt_id", UUID.class), rs.getString("model_version"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        ), learnerId) : List.of();
        return new AdaptiveLearnerProfileResponse(enabled, learnerId, states, recommendations);
    }

    public AdaptiveLearningOverviewResponse overview() {
        long learners = count("select count(distinct learner_id) from public.adaptive_learner_knowledge_states");
        long events = count("select count(*) from public.adaptive_learning_events where processed_at is not null");
        long recommendations = count("select count(*) from public.adaptive_recommendations where status = 'ACTIVE'");
        long accepted = count("select count(*) from public.adaptive_recommendations where accepted_at is not null");
        int components = Math.toIntExact(count("select count(*) from public.adaptive_knowledge_components where active"));
        int typeMappings = Math.toIntExact(count("select count(*) from public.adaptive_question_type_mappings"));
        int contentMappings = Math.toIntExact(count("select count(*) from public.adaptive_content_mappings where active"));
        List<AdaptiveLearningOverviewResponse.RecentLearner> recentLearners = jdbc.query("""
                with ranked as (
                  select state.learner_id, state.component_code, component.name component_name,
                    state.mastery_probability, state.confidence, state.evidence_count,
                    state.last_evidence_at,
                    row_number() over (
                      partition by state.learner_id
                      order by state.mastery_probability, state.updated_at desc
                    ) priority,
                    max(state.last_evidence_at) over (partition by state.learner_id) latest_evidence
                  from public.adaptive_learner_knowledge_states state
                  join public.adaptive_knowledge_components component on component.code = state.component_code
                )
                select ranked.learner_id, ranked.component_code, ranked.component_name,
                  ranked.mastery_probability, ranked.confidence, ranked.evidence_count,
                  ranked.last_evidence_at, recommendation.title recommendation_title
                from ranked
                left join public.adaptive_recommendations recommendation
                  on recommendation.learner_id = ranked.learner_id and recommendation.status = 'ACTIVE'
                where ranked.priority = 1
                order by ranked.latest_evidence desc nulls last
                limit 20
                """, (rs, ignored) -> new AdaptiveLearningOverviewResponse.RecentLearner(
                rs.getObject("learner_id", UUID.class), rs.getString("component_code"),
                rs.getString("component_name"), rs.getBigDecimal("mastery_probability"),
                rs.getBigDecimal("confidence"), rs.getInt("evidence_count"),
                rs.getObject("last_evidence_at", java.time.OffsetDateTime.class),
                rs.getString("recommendation_title")
        ));
        return new AdaptiveLearningOverviewResponse(
                enabled, learners, events, recommendations, accepted,
                components, typeMappings, contentMappings, recentLearners
        );
    }

    public AdaptiveKnowledgeGraphResponse knowledgeGraph() {
        List<AdaptiveKnowledgeGraphResponse.Component> components = jdbc.query("""
                select component.code, component.name, component.description, component.skill::text skill,
                  component.active,
                  (select count(*) from public.adaptive_question_type_mappings mapping
                    where mapping.component_code = component.code) question_type_mappings,
                  (select count(*) from public.adaptive_question_knowledge_mappings mapping
                    where mapping.component_code = component.code) question_mappings,
                  (select count(*) from public.adaptive_content_mappings mapping
                    where mapping.component_code = component.code and mapping.active) content_mappings,
                  (select count(distinct state.learner_id) from public.adaptive_learner_knowledge_states state
                    where state.component_code = component.code) learners_with_evidence
                from public.adaptive_knowledge_components component
                order by component.skill, component.name
                """, (rs, ignored) -> new AdaptiveKnowledgeGraphResponse.Component(
                rs.getString("code"), rs.getString("name"), rs.getString("description"),
                rs.getString("skill"), rs.getBoolean("active"),
                rs.getInt("question_type_mappings"), rs.getInt("question_mappings"),
                rs.getInt("content_mappings"), rs.getInt("learners_with_evidence")
        ));
        List<AdaptiveKnowledgeGraphResponse.Relation> relations = jdbc.query("""
                select prerequisite_code, target_code, relation_type
                from public.adaptive_knowledge_relations
                order by target_code, prerequisite_code
                """, (rs, ignored) -> new AdaptiveKnowledgeGraphResponse.Relation(
                rs.getString("prerequisite_code"), rs.getString("target_code"),
                rs.getString("relation_type")
        ));
        return new AdaptiveKnowledgeGraphResponse(enabled, components, relations);
    }

    public AdaptiveContentMappingsResponse contentMappings() {
        List<AdaptiveContentMappingsResponse.Component> components = jdbc.query("""
                select code, name, skill::text skill, active
                from public.adaptive_knowledge_components order by skill, name
                """, (rs, ignored) -> new AdaptiveContentMappingsResponse.Component(
                rs.getString("code"), rs.getString("name"), rs.getString("skill"), rs.getBoolean("active")
        ));
        List<AdaptiveContentMappingsResponse.Mapping> mappings = jdbc.query("""
                select mapping.id, mapping.component_code, component.name component_name,
                  mapping.target_type, mapping.target_id, mapping.title, mapping.href,
                  mapping.duration_minutes, mapping.difficulty, mapping.priority, mapping.active
                from public.adaptive_content_mappings mapping
                join public.adaptive_knowledge_components component on component.code = mapping.component_code
                order by mapping.active desc, mapping.priority desc, mapping.updated_at desc
                """, (rs, ignored) -> mapping(rs));
        return new AdaptiveContentMappingsResponse(enabled, components, mappings);
    }

    @Transactional
    public AdaptiveContentMappingsResponse.Mapping createContentMapping(UpsertAdaptiveContentMappingRequest request) {
        UUID id = UUID.randomUUID();
        validateContentMapping(null, request);
        jdbc.update("""
                insert into public.adaptive_content_mappings(
                  id, component_code, target_type, target_id, title, href,
                  duration_minutes, difficulty, priority, active
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, request.componentCode().trim(), request.targetType(), targetId(request),
                request.title().trim(), request.href().trim(), request.durationMinutes(),
                request.difficulty(), request.priority() == null ? 0 : request.priority(),
                request.active() == null || request.active());
        return contentMapping(id);
    }

    @Transactional
    public AdaptiveContentMappingsResponse.Mapping updateContentMapping(
            UUID id, UpsertAdaptiveContentMappingRequest request
    ) {
        validateContentMapping(id, request);
        int updated = jdbc.update("""
                update public.adaptive_content_mappings
                set component_code = ?, target_type = ?, target_id = ?, title = ?, href = ?,
                  duration_minutes = ?, difficulty = ?, priority = ?, active = ?, updated_at = now()
                where id = ?
                """, request.componentCode().trim(), request.targetType(), targetId(request),
                request.title().trim(), request.href().trim(), request.durationMinutes(),
                request.difficulty(), request.priority() == null ? 0 : request.priority(),
                request.active() == null || request.active(), id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Không tìm thấy content mapping");
        }
        return contentMapping(id);
    }

    private void validateContentMapping(UUID id, UpsertAdaptiveContentMappingRequest request) {
        Integer componentCount = jdbc.queryForObject("""
                select count(*) from public.adaptive_knowledge_components where code = ?
                """, Integer.class, request.componentCode().trim());
        if (componentCount == null || componentCount == 0) {
            throw new ResourceNotFoundException("Không tìm thấy knowledge component");
        }
        Integer duplicate = id == null
                ? jdbc.queryForObject("""
                        select count(*) from public.adaptive_content_mappings
                        where component_code = ? and target_type = ? and href = ?
                        """, Integer.class, request.componentCode().trim(), request.targetType(), request.href().trim())
                : jdbc.queryForObject("""
                        select count(*) from public.adaptive_content_mappings
                        where component_code = ? and target_type = ? and href = ? and id <> ?
                        """, Integer.class, request.componentCode().trim(), request.targetType(), request.href().trim(), id);
        if (duplicate != null && duplicate > 0) {
            throw new ConflictException("Mapping này đã tồn tại");
        }
    }

    private AdaptiveContentMappingsResponse.Mapping contentMapping(UUID id) {
        return jdbc.query("""
                select mapping.id, mapping.component_code, component.name component_name,
                  mapping.target_type, mapping.target_id, mapping.title, mapping.href,
                  mapping.duration_minutes, mapping.difficulty, mapping.priority, mapping.active
                from public.adaptive_content_mappings mapping
                join public.adaptive_knowledge_components component on component.code = mapping.component_code
                where mapping.id = ?
                """, (rs, ignored) -> mapping(rs), id).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy content mapping"));
    }

    private UUID targetId(UpsertAdaptiveContentMappingRequest request) {
        return request.targetId() == null || request.targetId().isBlank()
                ? null : UUID.fromString(request.targetId());
    }

    private AdaptiveContentMappingsResponse.Mapping mapping(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdaptiveContentMappingsResponse.Mapping(
                rs.getObject("id", UUID.class), rs.getString("component_code"),
                rs.getString("component_name"), rs.getString("target_type"),
                rs.getObject("target_id", UUID.class), rs.getString("title"), rs.getString("href"),
                rs.getObject("duration_minutes", Integer.class), rs.getString("difficulty"),
                rs.getInt("priority"), rs.getBoolean("active")
        );
    }

    @Transactional
    public void acceptRecommendation(UUID recommendationId, UUID learnerId) {
        if (!enabled) {
            return;
        }
        jdbc.update("""
                update public.adaptive_recommendations
                set accepted_at = coalesce(accepted_at, now()), updated_at = now()
                where id = ? and learner_id = ? and status = 'ACTIVE'
                """, recommendationId, learnerId);
    }

    private Map<String, String> explicitMappings(UUID testVersionId) {
        Map<String, String> values = new HashMap<>();
        jdbc.query("""
                select question_key, component_code
                from public.adaptive_question_knowledge_mappings
                where test_version_id = ? and primary_mapping = true
                """, new Object[]{testVersionId}, (RowCallbackHandler) rs -> values.put(
                rs.getString("question_key"), rs.getString("component_code")));
        return values;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private Map<String, String> typeMappings() {
        Map<String, String> values = new HashMap<>();
        jdbc.query("""
                select question_type, component_code from public.adaptive_question_type_mappings
                """, new Object[0], (RowCallbackHandler) rs -> values.put(
                rs.getString("question_type"), rs.getString("component_code")));
        return values;
    }

    private void updateState(ReadingAttemptGradedEvent event, String componentCode, boolean correct) {
        jdbc.update("""
                insert into public.adaptive_learner_knowledge_states(
                  learner_id, component_code, mastery_probability, confidence, evidence_count,
                  correct_count, model_version, last_evidence_at
                ) values (?, ?, 0.5000, 0, 0, 0, ?, null)
                on conflict (learner_id, component_code) do nothing
                """, event.learnerId(), componentCode, MODEL_VERSION);
        State state = jdbc.queryForObject("""
                select mastery_probability, evidence_count, correct_count
                from public.adaptive_learner_knowledge_states
                where learner_id = ? and component_code = ? for update
                """, (rs, ignored) -> new State(
                rs.getBigDecimal("mastery_probability"), rs.getInt("evidence_count"),
                rs.getInt("correct_count")
        ), event.learnerId(), componentCode);
        if (state == null) {
            throw new IllegalStateException("Adaptive learner state was not initialized");
        }
        int evidenceCount = state.evidenceCount() + 1;
        jdbc.update("""
                update public.adaptive_learner_knowledge_states
                set mastery_probability = ?, confidence = ?, evidence_count = ?, correct_count = ?,
                  model_version = ?, last_evidence_at = ?, updated_at = now()
                where learner_id = ? and component_code = ?
                """, MasteryUpdatePolicy.update(state.mastery(), correct),
                MasteryUpdatePolicy.confidence(evidenceCount), evidenceCount,
                state.correctCount() + (correct ? 1 : 0), MODEL_VERSION, event.submittedAt(),
                event.learnerId(), componentCode);
    }

    private void createRecommendation(ReadingAttemptGradedEvent event) {
        List<Candidate> candidates = jdbc.query("""
                select state.component_code, component.name component_name,
                  state.mastery_probability, state.confidence, state.evidence_count,
                  mapping.id content_mapping_id, mapping.title, mapping.href,
                  mapping.duration_minutes, mapping.difficulty
                from public.adaptive_learner_knowledge_states state
                join public.adaptive_knowledge_components component on component.code = state.component_code
                join public.adaptive_content_mappings mapping
                  on mapping.component_code = state.component_code and mapping.active = true
                where state.learner_id = ? and component.active = true
                order by state.mastery_probability, mapping.priority desc, mapping.created_at
                limit 1
                """, (rs, ignored) -> new Candidate(
                rs.getString("component_code"), rs.getString("component_name"),
                rs.getBigDecimal("mastery_probability"), rs.getBigDecimal("confidence"),
                rs.getInt("evidence_count"), rs.getObject("content_mapping_id", UUID.class),
                rs.getString("title"), rs.getString("href"),
                rs.getObject("duration_minutes", Integer.class), rs.getString("difficulty")
        ), event.learnerId());
        if (candidates.isEmpty()) {
            return;
        }
        Candidate candidate = candidates.getFirst();
        jdbc.update("""
                update public.adaptive_recommendations
                set status = 'SUPERSEDED', updated_at = now()
                where learner_id = ? and status = 'ACTIVE'
                """, event.learnerId());
        jdbc.update("""
                insert into public.adaptive_recommendations(
                  id, learner_id, component_code, content_mapping_id, source_attempt_id,
                  title, href, reason, duration_minutes, difficulty,
                  mastery_at_decision, confidence_at_decision, model_version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), event.learnerId(), candidate.componentCode(),
                candidate.contentMappingId(), event.attemptId(), candidate.title(), candidate.href(),
                reason(candidate), candidate.durationMinutes(), candidate.difficulty(),
                candidate.mastery(), candidate.confidence(), MODEL_VERSION);
    }

    static String reason(Candidate candidate) {
        int percent = candidate.mastery().multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        return "Mức độ thành thạo “%s” hiện được ước lượng %d%% từ %d bằng chứng. "
                .formatted(candidate.componentName(), percent, candidate.evidenceCount())
                + "Bài luyện này giúp củng cố đúng kỹ năng đang cần ưu tiên.";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể lưu sự kiện Adaptive Learning", exception);
        }
    }

    private record State(BigDecimal mastery, int evidenceCount, int correctCount) {
    }

    record Candidate(
            String componentCode,
            String componentName,
            BigDecimal mastery,
            BigDecimal confidence,
            int evidenceCount,
            UUID contentMappingId,
            String title,
            String href,
            Integer durationMinutes,
            String difficulty
    ) {
    }
}

