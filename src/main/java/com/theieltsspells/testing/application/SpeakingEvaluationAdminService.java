package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.testing.application.dto.AdminSpeakingEvaluationResponse;
import com.theieltsspells.testing.application.dto.ReviewSpeakingEvaluationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingEvaluationAdminService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<AdminSpeakingEvaluationResponse> list() {
        return jdbc.query(selectSql() + " order by evaluation.created_at desc limit 200", this::map);
    }

    public AdminSpeakingEvaluationResponse get(UUID id) {
        var values = jdbc.query(selectSql() + " where evaluation.id=?", this::map, id);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Speaking cần chấm");
        return values.getFirst();
    }

    @Transactional
    public AdminSpeakingEvaluationResponse review(UUID id, ReviewSpeakingEvaluationRequest request, UUID reviewerId) {
        jdbc.queryForList("select id from public.speaking_evaluations where id=? for update", id);
        var current = get(id);
        List.of(request.overallBand(), request.fluencyBand(), request.lexicalBand(), request.grammarBand(),
                request.pronunciationBand()).forEach(this::validateBand);
        if ("PUBLISHED".equals(current.teacherStatus()) && !request.publish()) {
            throw new ConflictException("Bài Speaking đã công bố phải được lưu và công bố lại khi chỉnh sửa");
        }
        jdbc.update("""
                update public.speaking_evaluations set teacher_status=?, overall_band_teacher=?,
                  fluency_band_teacher=?, lexical_band_teacher=?, grammar_band_teacher=?,
                  pronunciation_band_teacher=?, teacher_feedback=?, teacher_strengths=cast(? as jsonb),
                  teacher_improvements=cast(? as jsonb), reviewed_by=?, reviewed_at=now(),
                  published_at=case when ? then now() else null end, updated_at=now()
                where id=?
                """, request.publish() ? "PUBLISHED" : "REVIEWED", request.overallBand(),
                request.fluencyBand(), request.lexicalBand(), request.grammarBand(), request.pronunciationBand(),
                blankToNull(request.feedback()), json(safe(request.strengths())), json(safe(request.improvements())),
                reviewerId, request.publish(), id);
        return get(id);
    }

    private String selectSql() {
        return """
                select evaluation.id, evaluation.attempt_id, evaluation.student_id,
                  coalesce(profile.full_name, profile.email, 'Học viên') student_name,
                  version.title test_title, evaluation.question_key, evaluation.audio_url,
                  evaluation.transcript, evaluation.status ai_status, evaluation.overall_band,
                  evaluation.fluency_band, evaluation.lexical_band, evaluation.grammar_band,
                  evaluation.pronunciation_band, evaluation.fluency_feedback, evaluation.lexical_feedback,
                  evaluation.grammar_feedback, evaluation.pronunciation_feedback,
                  evaluation.strengths::text ai_strengths, evaluation.improvements::text ai_improvements,
                  evaluation.teacher_status, evaluation.overall_band_teacher,
                  evaluation.fluency_band_teacher, evaluation.lexical_band_teacher,
                  evaluation.grammar_band_teacher, evaluation.pronunciation_band_teacher,
                  evaluation.teacher_feedback, evaluation.teacher_strengths::text teacher_strengths,
                  evaluation.teacher_improvements::text teacher_improvements,
                  evaluation.reviewed_at, evaluation.published_at, evaluation.created_at
                from public.speaking_evaluations evaluation
                join public.test_attempts attempt on attempt.id=evaluation.attempt_id
                join public.test_versions version on version.id=attempt.test_version_id
                left join public.profiles profile on profile.id=evaluation.student_id
                """;
    }

    private AdminSpeakingEvaluationResponse map(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        var criteria = new LinkedHashMap<String, AdminSpeakingEvaluationResponse.Criterion>();
        criteria.put("fluency_coherence", criterion(rs, "fluency"));
        criteria.put("lexical_resource", criterion(rs, "lexical"));
        criteria.put("grammatical_range_accuracy", criterion(rs, "grammar"));
        criteria.put("pronunciation", criterion(rs, "pronunciation"));
        return new AdminSpeakingEvaluationResponse(rs.getObject("id", UUID.class),
                rs.getObject("attempt_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("student_name"), rs.getString("test_title"), rs.getString("question_key"),
                rs.getString("audio_url"), rs.getString("transcript"), rs.getString("ai_status"),
                rs.getBigDecimal("overall_band"), criteria, strings(rs.getString("ai_strengths")),
                strings(rs.getString("ai_improvements")), rs.getString("teacher_status"),
                rs.getBigDecimal("overall_band_teacher"), rs.getString("teacher_feedback"),
                strings(rs.getString("teacher_strengths")), strings(rs.getString("teacher_improvements")),
                rs.getObject("reviewed_at", java.time.OffsetDateTime.class),
                rs.getObject("published_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }

    private AdminSpeakingEvaluationResponse.Criterion criterion(java.sql.ResultSet rs, String prefix)
            throws java.sql.SQLException {
        return new AdminSpeakingEvaluationResponse.Criterion(rs.getBigDecimal(prefix + "_band"),
                rs.getBigDecimal(prefix + "_band_teacher"), rs.getString(prefix + "_feedback"));
    }

    private void validateBand(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(9)) > 0
                || value.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("Band Speaking phải từ 0 đến 9 theo bước 0.5");
        }
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).toList();
    }
    private List<String> strings(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception ignored) { return List.of(); }
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new BusinessRuleException("Không thể lưu nhận xét Speaking"); }
    }
}
