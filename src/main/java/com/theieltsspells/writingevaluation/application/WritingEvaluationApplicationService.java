package com.theieltsspells.writingevaluation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.writingevaluation.application.dto.ReviewWritingEvaluationRequest;
import com.theieltsspells.writingevaluation.application.dto.WritingEvaluationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WritingEvaluationApplicationService {
    private static final Set<String> CRITERIA = Set.of("task_achievement", "task_response", "coherence_cohesion", "lexical_resource", "grammatical_range_accuracy");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<WritingEvaluationResponse> list(String status) {
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return jdbc.query(selectSql() + (filter == null ? "" : " where evaluation.status=cast(? as public.evaluation_status)") +
                " order by evaluation.created_at desc limit 200", this::map, filter == null ? new Object[]{} : new Object[]{filter});
    }

    public WritingEvaluationResponse get(UUID id) {
        var values = jdbc.query(selectSql() + " where evaluation.id=?", this::map, id);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Writing cần chấm");
        return values.getFirst();
    }

    @Transactional
    public WritingEvaluationResponse review(UUID id, ReviewWritingEvaluationRequest request, UUID reviewerId) {
        var evaluation = get(id);
        validateBand(request.overallBand());
        var expected = new java.util.HashSet<>(List.of("coherence_cohesion", "lexical_resource", "grammatical_range_accuracy"));
        expected.add("task_2".equals(evaluation.taskType()) ? "task_response" : "task_achievement");
        if (!request.criteria().keySet().equals(expected) || request.criteria().keySet().stream().anyMatch(key -> !CRITERIA.contains(key))) {
            throw new BusinessRuleException("Bài Writing phải được chấm đủ đúng bốn tiêu chí IELTS");
        }
        request.criteria().forEach((criterion, value) -> {
            validateBand(value.band());
            jdbc.update("""
                    insert into public.writing_criterion_scores(evaluation_id, criterion, teacher_band,
                      teacher_feedback, evidence)
                    values (?, ?, ?, ?, '{}'::jsonb)
                    on conflict (evaluation_id, criterion) do update set teacher_band=excluded.teacher_band,
                      teacher_feedback=excluded.teacher_feedback
                    """, id, criterion, value.band(), blankToNull(value.feedback()));
        });
        String nextStatus = request.publish() ? "PUBLISHED" : "TEACHER_REVIEWED";
        jdbc.update("""
                update public.writing_evaluations set overall_band_teacher=?, strengths=cast(? as jsonb),
                  improvements=cast(? as jsonb), status=cast(? as public.evaluation_status), reviewed_by=?,
                  reviewed_at=now(), published_at=case when ? then now() else null end, updated_at=now()
                where id=?
                """, request.overallBand(), json(safe(request.strengths())), json(safe(request.improvements())),
                nextStatus, reviewerId, request.publish(), id);
        return get(id);
    }

    private String selectSql() {
        return """
                select evaluation.id, attempt.id attempt_id, attempt.student_id,
                  coalesce(profile.full_name, profile.email, 'Học viên') student_name,
                  version.title test_title, response.question_key task_key, evaluation.task_type,
                  evaluation.essay_text, evaluation.status::text status, evaluation.overall_band_ai,
                  evaluation.overall_band_teacher, evaluation.strengths::text strengths,
                  evaluation.improvements::text improvements, evaluation.error_message,
                  evaluation.reviewed_at, evaluation.published_at, evaluation.created_at
                from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                join public.test_attempts attempt on attempt.id=response.attempt_id
                join public.test_versions version on version.id=attempt.test_version_id
                left join public.profiles profile on profile.id=attempt.student_id
                """;
    }

    private WritingEvaluationResponse map(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new WritingEvaluationResponse(id, rs.getObject("attempt_id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getString("student_name"), rs.getString("test_title"),
                rs.getString("task_key"), rs.getString("task_type"), rs.getString("essay_text"),
                rs.getString("status"), rs.getBigDecimal("overall_band_ai"), rs.getBigDecimal("overall_band_teacher"),
                criteria(id), strings(rs.getString("strengths")), strings(rs.getString("improvements")),
                rs.getString("error_message"), rs.getObject("reviewed_at", java.time.OffsetDateTime.class),
                rs.getObject("published_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }

    private Map<String, WritingEvaluationResponse.Criterion> criteria(UUID id) {
        var result = new LinkedHashMap<String, WritingEvaluationResponse.Criterion>();
        jdbc.query("""
                select criterion, ai_band, teacher_band, ai_feedback, teacher_feedback
                from public.writing_criterion_scores where evaluation_id=? order by criterion
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getString("criterion"),
                new WritingEvaluationResponse.Criterion(rs.getBigDecimal("ai_band"), rs.getBigDecimal("teacher_band"),
                        rs.getString("ai_feedback"), rs.getString("teacher_feedback"))), id);
        return result;
    }

    private void validateBand(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(9)) > 0
                || value.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("Band Writing phải từ 0 đến 9 theo bước 0.5");
        }
    }

    private List<String> safe(List<String> values) { return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private List<String> strings(String value) { try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ignored) { return List.of(); } }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new BusinessRuleException("Không thể lưu nhận xét Writing"); } }
}
