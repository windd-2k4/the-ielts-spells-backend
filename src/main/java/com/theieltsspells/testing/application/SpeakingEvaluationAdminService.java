package com.theieltsspells.testing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.SpeakingBandScorePolicy;
import com.theieltsspells.testing.application.dto.AdminSpeakingEvaluationResponse;
import com.theieltsspells.testing.application.dto.ReviewSpeakingEvaluationRequest;
import com.theieltsspells.testing.application.dto.SpeakingAiEntitlementResponse;
import com.theieltsspells.testing.application.dto.UpdateSpeakingAiEntitlementRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingEvaluationAdminService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final com.theieltsspells.notification.WritingNotificationService notifications;

    public List<AdminSpeakingEvaluationResponse> list() {
        return list(null, true);
    }

    public List<AdminSpeakingEvaluationResponse> list(UUID teacherId, boolean globalScope) {
        String scope = globalScope ? "" : " and exists (select 1 from public.test_assignments teacher_assignment "
                + "where teacher_assignment.id=attempt.test_assignment_id and exists (select 1 from public.course_teachers teacher_scope "
                + "where teacher_scope.course_id=teacher_assignment.course_id and teacher_scope.teacher_id=?))";
        return jdbc.query(selectSql() + " where evaluation.question_key='" + SpeakingEvaluationService.ATTEMPT_KEY
                + "' and (attempt.attempt_origin='SELF_PRACTICE' or not exists (select 1 from public.test_attempts newer "
                + "where newer.test_assignment_id=attempt.test_assignment_id and newer.student_id=attempt.student_id "
                + "and newer.attempt_no > attempt.attempt_no))" + scope
                + " order by evaluation.created_at desc limit 200", this::map,
                globalScope ? new Object[]{} : new Object[]{teacherId});
    }

    public AdminSpeakingEvaluationResponse get(UUID id) {
        return get(id, null, true);
    }

    public AdminSpeakingEvaluationResponse get(UUID id, UUID teacherId, boolean globalScope) {
        String scope = globalScope ? "" : " and exists (select 1 from public.test_assignments teacher_assignment "
                + "where teacher_assignment.id=attempt.test_assignment_id and exists (select 1 from public.course_teachers teacher_scope "
                + "where teacher_scope.course_id=teacher_assignment.course_id and teacher_scope.teacher_id=?))";
        var values = jdbc.query(selectSql() + " where evaluation.id=?" + scope, this::map,
                globalScope ? new Object[]{id} : new Object[]{id, teacherId});
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Speaking cần chấm");
        return values.getFirst();
    }

    @Transactional
    public AdminSpeakingEvaluationResponse review(UUID id, ReviewSpeakingEvaluationRequest request, UUID reviewerId) {
        return review(id, request, reviewerId, true);
    }

    @Transactional
    public AdminSpeakingEvaluationResponse review(UUID id, ReviewSpeakingEvaluationRequest request, UUID reviewerId,
                                                  boolean globalScope) {
        jdbc.queryForList("select id from public.speaking_evaluations where id=? for update", id);
        var current = get(id, reviewerId, globalScope);
        List.of(request.fluencyBand(), request.lexicalBand(), request.grammarBand(),
                request.pronunciationBand()).forEach(this::validateBand);
        BigDecimal overall = SpeakingBandScorePolicy.overall(List.of(request.fluencyBand(), request.lexicalBand(),
                request.grammarBand(), request.pronunciationBand()));
        if ("PUBLISHED".equals(current.teacherStatus()) && !request.publish()) {
            throw new ConflictException("Bài Speaking đã công bố phải được lưu và công bố lại khi chỉnh sửa");
        }
        jdbc.update("""
                update public.speaking_evaluations set teacher_status=?, overall_band_teacher=?,
                  fluency_band_teacher=?, lexical_band_teacher=?, grammar_band_teacher=?,
                  pronunciation_band_teacher=?, teacher_feedback=?, teacher_strengths=cast(? as jsonb),
                  teacher_improvements=cast(? as jsonb), reviewed_by=?, reviewed_at=now(),
                  fluency_feedback_teacher=?, lexical_feedback_teacher=?, grammar_feedback_teacher=?,
                  pronunciation_feedback_teacher=?,
                  published_at=case when ? then now() else null end, updated_at=now()
                where id=?
                """, request.publish() ? "PUBLISHED" : "REVIEWED", overall,
                request.fluencyBand(), request.lexicalBand(), request.grammarBand(), request.pronunciationBand(),
                blankToNull(request.feedback()), json(safe(request.strengths())), json(safe(request.improvements())),
                reviewerId, criterionFeedback(request, "fluency_coherence"),
                criterionFeedback(request, "lexical_resource"),
                criterionFeedback(request, "grammatical_range_accuracy"),
                criterionFeedback(request, "pronunciation"), request.publish(), id);
        if (request.publish()) notifications.speakingCompleted(id, true);
        return get(id, reviewerId, globalScope);
    }

    public List<SpeakingAiEntitlementResponse> listEntitlements(String query) {
        String filter = query == null ? "" : query.trim().toLowerCase();
        return jdbc.query("""
                select student.user_id, student.student_code,
                  coalesce(profile.full_name, profile.email, 'Học viên') student_name, profile.email,
                  entitlement.enabled, entitlement.unlimited, entitlement.monthly_limit,
                  entitlement.active_from, entitlement.expires_at, entitlement.source, entitlement.note,
                  (select count(*) from public.speaking_evaluations evaluation
                   where evaluation.student_id=student.user_id and evaluation.question_key=?
                     and evaluation.ai_requested_at >= date_trunc('month', now())) used_this_month
                from public.student_profiles student join public.profiles profile on profile.id=student.user_id
                left join public.speaking_ai_entitlements entitlement on entitlement.student_id=student.user_id
                where (?='' or lower(coalesce(profile.full_name, '')) like '%' || ? || '%'
                  or lower(coalesce(profile.email, '')) like '%' || ? || '%'
                  or lower(student.student_code) like '%' || ? || '%' or cast(student.user_id as text)=?)
                order by entitlement.enabled desc nulls last, profile.full_name, student.student_code limit 500
                """, (rs, ignored) -> entitlement(rs), SpeakingEvaluationService.ATTEMPT_KEY,
                filter, filter, filter, filter, filter);
    }

    @Transactional
    public SpeakingAiEntitlementResponse updateEntitlement(UUID studentId,
                                                            UpdateSpeakingAiEntitlementRequest request,
                                                            UUID grantedBy) {
        if (request.mode() == UpdateSpeakingAiEntitlementRequest.Mode.LIMITED && request.monthlyLimit() == null) {
            throw new BusinessRuleException("Chế độ giới hạn cần số lượt chấm mỗi tháng");
        }
        OffsetDateTime activeFrom = request.activeFrom() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.activeFrom();
        OffsetDateTime expiresAt = request.mode() == UpdateSpeakingAiEntitlementRequest.Mode.DISABLED ? null : request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(activeFrom)) throw new BusinessRuleException("Ngày hết hạn phải sau ngày bắt đầu");
        Long exists = jdbc.queryForObject("select count(*) from public.student_profiles where user_id=?", Long.class, studentId);
        if (exists == null || exists == 0) throw new ResourceNotFoundException("Không tìm thấy học viên");
        Integer limit = request.mode() == UpdateSpeakingAiEntitlementRequest.Mode.LIMITED ? request.monthlyLimit() : null;
        jdbc.update("""
                insert into public.speaking_ai_entitlements(student_id, enabled, unlimited, monthly_limit,
                  active_from, expires_at, source, note, granted_by)
                values (?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?)
                on conflict (student_id) do update set enabled=excluded.enabled, unlimited=excluded.unlimited,
                  monthly_limit=excluded.monthly_limit, active_from=excluded.active_from,
                  expires_at=excluded.expires_at, source='MANUAL', note=excluded.note,
                  granted_by=excluded.granted_by, updated_at=now()
                """, studentId, request.mode() != UpdateSpeakingAiEntitlementRequest.Mode.DISABLED,
                request.mode() == UpdateSpeakingAiEntitlementRequest.Mode.UNLIMITED, limit, activeFrom,
                expiresAt, blankToNull(request.note()), grantedBy);
        if (request.mode() == UpdateSpeakingAiEntitlementRequest.Mode.DISABLED) {
            jdbc.update("update public.speaking_evaluations set status='NOT_ELIGIBLE', updated_at=now() " +
                    "where student_id=? and question_key=? and status='QUEUED'", studentId, SpeakingEvaluationService.ATTEMPT_KEY);
        }
        return listEntitlements(studentId.toString()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học viên"));
    }

    private String selectSql() {
        return """
                select evaluation.id, evaluation.attempt_id, evaluation.student_id,
                  coalesce(profile.full_name, profile.email, 'Học viên') student_name,
                  version.title test_title, evaluation.question_key, evaluation.audio_url,
                  evaluation.transcript,
                  (select coalesce(jsonb_agg(jsonb_build_object(
                    'questionKey', response.question_key,
                    'audioUrl', response.answer->>'audioUrl',
                    'transcript', response.answer->>'transcript') order by response.answered_at), '[]'::jsonb)::text
                   from public.test_attempt_responses response
                   where response.attempt_id=evaluation.attempt_id and response.question_type='SPEAKING_QUESTION') segments,
                  evaluation.status ai_status, evaluation.overall_band,
                  evaluation.fluency_band, evaluation.lexical_band, evaluation.grammar_band,
                  evaluation.pronunciation_band, evaluation.fluency_feedback, evaluation.lexical_feedback,
                  evaluation.grammar_feedback, evaluation.pronunciation_feedback,
                  evaluation.strengths::text ai_strengths, evaluation.improvements::text ai_improvements,
                  evaluation.teacher_status, evaluation.overall_band_teacher,
                  evaluation.fluency_band_teacher, evaluation.lexical_band_teacher,
                  evaluation.grammar_band_teacher, evaluation.pronunciation_band_teacher,
                  evaluation.teacher_feedback, evaluation.teacher_strengths::text teacher_strengths,
                  evaluation.teacher_improvements::text teacher_improvements,
                  evaluation.fluency_feedback_teacher, evaluation.lexical_feedback_teacher,
                  evaluation.grammar_feedback_teacher, evaluation.pronunciation_feedback_teacher,
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
                rs.getString("audio_url"), rs.getString("transcript"), segments(rs.getString("segments")), rs.getString("ai_status"),
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
                rs.getBigDecimal(prefix + "_band_teacher"), rs.getString(prefix + "_feedback"),
                rs.getString(prefix + "_feedback_teacher"));
    }

    private String criterionFeedback(ReviewSpeakingEvaluationRequest request, String key) {
        return request.criterionFeedback() == null ? null : blankToNull(request.criterionFeedback().get(key));
    }

    private SpeakingAiEntitlementResponse entitlement(java.sql.ResultSet rs) throws java.sql.SQLException {
        boolean enabled = rs.getObject("enabled") != null && rs.getBoolean("enabled");
        boolean unlimited = enabled && rs.getBoolean("unlimited");
        String mode = !enabled ? "DISABLED" : unlimited ? "UNLIMITED" : "LIMITED";
        return new SpeakingAiEntitlementResponse(rs.getObject("user_id", UUID.class), rs.getString("student_code"),
                rs.getString("student_name"), rs.getString("email"), mode,
                rs.getObject("monthly_limit", Integer.class), rs.getLong("used_this_month"),
                rs.getObject("active_from", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getString("source"), rs.getString("note"));
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
    private List<AdminSpeakingEvaluationResponse.Segment> segments(String value) {
        try {
            List<java.util.Map<String, Object>> rows = objectMapper.readValue(value, new TypeReference<>() {});
            return rows.stream().map(row -> new AdminSpeakingEvaluationResponse.Segment(
                    String.valueOf(row.getOrDefault("questionKey", "")),
                    row.get("audioUrl") == null ? null : row.get("audioUrl").toString(),
                    row.get("transcript") == null ? null : row.get("transcript").toString())).toList();
        } catch (Exception ignored) { return List.of(); }
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new BusinessRuleException("Không thể lưu nhận xét Speaking"); }
    }
}
