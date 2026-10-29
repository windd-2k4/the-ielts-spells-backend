package com.theieltsspells.writingevaluation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.application.WritingSubmittedEvent;
import com.theieltsspells.shared.WritingBandScorePolicy;
import com.theieltsspells.shared.util.HtmlSanitizer;
import com.theieltsspells.writingevaluation.application.dto.ReviewWritingEvaluationRequest;
import com.theieltsspells.writingevaluation.application.dto.UpdateWritingAiEntitlementRequest;
import com.theieltsspells.writingevaluation.application.dto.WritingAiEntitlementResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingEvaluationFilterOptionsResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingEvaluationResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingCalibrationResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingTeacherAnnotation;
import com.theieltsspells.writingevaluation.application.dto.WritingTeacherDraftRequest;
import com.theieltsspells.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.ArrayList;
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
    private final com.theieltsspells.notification.WritingNotificationService notifications;
    private final com.theieltsspells.academic.application.AcademicMembershipService memberships;

    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(WritingSubmittedEvent source) {
        UUID studentId = jdbc.queryForObject("""
                select attempt.student_id from public.test_attempt_responses response
                join public.test_attempts attempt on attempt.id=response.attempt_id
                where response.id=?
                """, UUID.class, source.responseId());
        jdbc.queryForObject("select user_id from public.student_profiles where user_id=? for update", UUID.class, studentId);
        jdbc.update("""
                insert into public.writing_evaluations(test_attempt_response_id, task_type, essay_text,
                  task_context, status, rubric_version, prompt_version)
                values (?, ?, ?, cast(? as jsonb), cast(? as public.evaluation_status),
                  'ielts-writing-public-may-2023-v1', 'writing-evaluator-v3')
                on conflict (test_attempt_response_id) where test_attempt_response_id is not null do nothing
                """, source.responseId(), source.taskType(), source.essayText(), json(source.taskContext()), "NOT_ELIGIBLE");
    }

    @Transactional
    public int requestAi(UUID attemptId, UUID studentId) {
        var attempts = jdbc.queryForList("""
                select attempt.id from public.test_attempts attempt
                join public.test_versions version on version.id=attempt.test_version_id
                where attempt.id=? and attempt.student_id=? and attempt.status <> 'IN_PROGRESS'
                  and version.primary_skill='WRITING' for update of attempt
                """, UUID.class, attemptId, studentId);
        if (attempts.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Writing đã nộp");
        var assignments = jdbc.queryForList("""
                select assignment.course_id, assignment.show_result_after_submit
                from public.test_attempts attempt join public.test_assignments assignment on assignment.id=attempt.test_assignment_id
                where attempt.id=?
                """, attemptId);
        if (!assignments.isEmpty()) {
            var assignment = assignments.getFirst();
            if (!memberships.hasActiveEnrollment((UUID) assignment.get("course_id"), studentId)
                    || !Boolean.TRUE.equals(assignment.get("show_result_after_submit"))) {
                throw new BusinessRuleException("Bài trong khóa chưa khả dụng để yêu cầu TIS chấm");
            }
        }
        jdbc.queryForObject("select user_id from public.student_profiles where user_id=? for update", UUID.class, studentId);
        jdbc.update("""
                update public.writing_evaluations evaluation set ai_requested_at=null
                from public.test_attempt_responses response
                where evaluation.test_attempt_response_id=response.id and response.attempt_id=?
                  and evaluation.status='FAILED'
                """, attemptId);
        var requestable = jdbc.queryForList("""
                select evaluation.id from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                where response.attempt_id=? and evaluation.status in ('NOT_ELIGIBLE','FAILED')
                order by evaluation.created_at for update of evaluation
                """, UUID.class, attemptId);
        if (requestable.isEmpty()) return 0;
        if (!mayUseAi(studentId, requestable.size())) {
            throw new BusinessRuleException("Bạn chưa được cấp lượt chấm Writing bằng TIS hoặc đã dùng hết ngân sách tháng này");
        }
        return jdbc.update("""
                update public.writing_evaluations evaluation set status='QUEUED', error_message=null, ai_requested_at=now(), updated_at=now()
                from public.test_attempt_responses response
                where evaluation.test_attempt_response_id=response.id and response.attempt_id=?
                  and evaluation.status in ('NOT_ELIGIBLE','FAILED')
                """, attemptId);
    }

    @Transactional
    public WritingEvaluationResponse retry(UUID id) {
        return retry(id, null, true);
    }

    @Transactional
    public WritingEvaluationResponse retry(UUID id, UUID teacherId, boolean globalScope) {
        var evaluation = get(id, teacherId, globalScope);
        if (!"FAILED".equals(evaluation.status())) {
            throw new ConflictException("Chỉ có thể thử lại bài AI chấm lỗi");
        }
        if (!hasActiveEntitlement(evaluation.studentId())) {
            throw new BusinessRuleException("Học viên không có quyền AI Writing đang hiệu lực");
        }
        int updated = jdbc.update("""
                update public.writing_evaluations set status='QUEUED', error_message=null,
                  processing_token=null, processing_started_at=null, updated_at=now()
                where id=? and status='FAILED'
                """, id);
        if (updated != 1) throw new ConflictException("Chỉ có thể thử lại bài AI chấm lỗi");
        return get(id, teacherId, globalScope);
    }

    public PageResponse<WritingEvaluationResponse> list(String status, String origin, UUID courseId,
                                                         String teacherStatus, String query, int page, int size) {
        return list(status, origin, courseId, teacherStatus, query, page, size, null, true);
    }

    public PageResponse<WritingEvaluationResponse> list(String status, String origin, UUID courseId,
                                                         String teacherStatus, String query, int page, int size,
                                                         UUID teacherId, boolean globalScope) {
        String aiFilter = normalize(status);
        String originFilter = normalize(origin);
        String teacherFilter = normalize(teacherStatus);
        if (aiFilter != null && !Set.of("QUEUED", "PROCESSING", "AI_COMPLETED", "FAILED", "NOT_ELIGIBLE").contains(aiFilter)) {
            throw new BusinessRuleException("Trạng thái chấm Writing không hợp lệ");
        }
        if (originFilter == null) originFilter = "ALL";
        if (!Set.of("ALL", "SELF_PRACTICE", "ASSIGNMENT").contains(originFilter)) {
            throw new BusinessRuleException("Nguồn bài Writing không hợp lệ");
        }
        if (teacherFilter != null && !Set.of("AVAILABLE", "REVIEWED", "PUBLISHED").contains(teacherFilter)) {
            throw new BusinessRuleException("Trạng thái giáo viên không hợp lệ");
        }
        if (page < 0) throw new BusinessRuleException("Trang danh sách không hợp lệ");
        int pageSize = Math.max(1, Math.min(size, 100));
        var where = new StringBuilder(" where 1=1");
        var parameters = new ArrayList<Object>();
        if (aiFilter != null) {
            where.append(" and evaluation.status=cast(? as public.evaluation_status)");
            parameters.add(aiFilter);
        }
        if ("SELF_PRACTICE".equals(originFilter)) {
            where.append(" and attempt.attempt_origin='SELF_PRACTICE'");
        } else if ("ASSIGNMENT".equals(originFilter)) {
            where.append(" and attempt.attempt_origin='ASSIGNMENT' and course.skill_pair='SPEAKING_WRITING'");
        }
        if (courseId != null) {
            where.append(" and assignment.course_id=? and course.skill_pair='SPEAKING_WRITING'");
            parameters.add(courseId);
        }
        if (teacherFilter != null) {
            where.append(" and evaluation.teacher_status=?");
            parameters.add(teacherFilter);
        }
        String search = query == null ? "" : query.trim().toLowerCase();
        if (!search.isEmpty()) {
            where.append(" and (lower(coalesce(profile.full_name, '')) like ? or lower(coalesce(profile.email, '')) like ? or lower(student.student_code) like ? or lower(version.title) like ?)");
            String pattern = "%" + search + "%";
            parameters.add(pattern); parameters.add(pattern); parameters.add(pattern); parameters.add(pattern);
        }
        where.append(" and (attempt.attempt_origin='SELF_PRACTICE' or not exists (select 1 from public.test_attempts newer "
                + "where newer.test_assignment_id=attempt.test_assignment_id and newer.student_id=attempt.student_id "
                + "and newer.attempt_no > attempt.attempt_no))");
        if (!globalScope) {
            where.append(" and exists (select 1 from public.course_teachers teacher_scope "
                    + "where teacher_scope.course_id=assignment.course_id and teacher_scope.teacher_id=?)");
            parameters.add(teacherId);
        }
        Long total = jdbc.queryForObject("select count(*)" + fromSql() + where, Long.class, parameters.toArray());
        var pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageSize);
        pageParameters.add((long) page * pageSize);
        var content = jdbc.query(selectSql() + where + " order by evaluation.created_at desc limit ? offset ?",
                this::map, pageParameters.toArray());
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return new PageResponse<>(content, page, pageSize, totalElements, totalPages, page == 0, page + 1 >= totalPages);
    }

    public WritingEvaluationFilterOptionsResponse filterOptions() {
        return filterOptions(null, true);
    }

    public WritingEvaluationFilterOptionsResponse filterOptions(UUID teacherId, boolean globalScope) {
        String courseScope = globalScope ? "" : " and exists (select 1 from public.course_teachers teacher_scope "
                + "where teacher_scope.course_id=course.id and teacher_scope.teacher_id=?)";
        var courses = jdbc.query("""
                select course.id, course.code, course.name, course.status::text status,
                  count(distinct attempt.student_id) student_count,
                  count(evaluation.id) evaluation_count,
                  count(evaluation.id) filter (where evaluation.teacher_status='AVAILABLE') awaiting_teacher_count
                from public.courses course
                join public.test_assignments assignment on assignment.course_id=course.id
                join public.test_attempts attempt on attempt.test_assignment_id=assignment.id
                join public.test_attempt_responses response on response.attempt_id=attempt.id
                join public.writing_evaluations evaluation on evaluation.test_attempt_response_id=response.id
                where course.skill_pair='SPEAKING_WRITING'
                  and (attempt.attempt_origin='SELF_PRACTICE' or not exists (
                    select 1 from public.test_attempts newer
                    where newer.test_assignment_id=attempt.test_assignment_id
                      and newer.student_id=attempt.student_id
                      and newer.attempt_no > attempt.attempt_no
                  ))
                """ + courseScope + """
                group by course.id, course.code, course.name, course.status, course.starts_on
                order by case when course.status::text in ('active', 'open') then 0 else 1 end,
                  course.starts_on desc, course.code
                """, (rs, ignored) -> new WritingEvaluationFilterOptionsResponse.CourseOption(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("status"), rs.getLong("student_count"), rs.getLong("evaluation_count"),
                rs.getLong("awaiting_teacher_count")), globalScope ? new Object[]{} : new Object[]{teacherId});
        String countScope = " where (attempt.attempt_origin='SELF_PRACTICE' or not exists ("
                + "select 1 from public.test_attempts newer "
                + "where newer.test_assignment_id=attempt.test_assignment_id "
                + "and newer.student_id=attempt.student_id "
                + "and newer.attempt_no > attempt.attempt_no))";
        if (!globalScope) {
            countScope += " and exists (select 1 from public.course_teachers teacher_scope "
                    + "where teacher_scope.course_id=course.id and teacher_scope.teacher_id=?)";
        }
        var counts = jdbc.queryForMap("""
                select count(*) total_count,
                  count(*) filter (where attempt.attempt_origin='SELF_PRACTICE') self_practice_count,
                  count(*) filter (where attempt.attempt_origin='ASSIGNMENT' and course.skill_pair='SPEAKING_WRITING') course_count
                from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                join public.test_attempts attempt on attempt.id=response.attempt_id
                left join public.test_assignments assignment on assignment.id=attempt.test_assignment_id
                left join public.courses course on course.id=assignment.course_id
                """ + countScope, globalScope ? new Object[]{} : new Object[]{teacherId});
        return new WritingEvaluationFilterOptionsResponse(number(counts.get("total_count")),
                number(counts.get("self_practice_count")), number(counts.get("course_count")), courses);
    }

    public WritingEvaluationResponse get(UUID id) {
        return get(id, null, true);
    }

    public WritingEvaluationResponse get(UUID id, UUID teacherId, boolean globalScope) {
        String scope = globalScope ? "" : " and exists (select 1 from public.course_teachers teacher_scope "
                + "where teacher_scope.course_id=assignment.course_id and teacher_scope.teacher_id=?)";
        var parameters = globalScope ? new Object[]{id} : new Object[]{id, teacherId};
        var values = jdbc.query(selectSql() + " where evaluation.id=?" + scope, this::map, parameters);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy bài Writing cần chấm");
        return values.getFirst();
    }

    @Transactional
    public WritingEvaluationResponse review(UUID id, ReviewWritingEvaluationRequest request, UUID reviewerId) {
        return review(id, request, reviewerId, true);
    }

    @Transactional
    public WritingEvaluationResponse review(UUID id, ReviewWritingEvaluationRequest request, UUID reviewerId,
                                            boolean globalScope) {
        jdbc.queryForList("select id from public.writing_evaluations where id=? for update", id);
        var evaluation = get(id, reviewerId, globalScope);
        validateBand(request.overallBand());
        var expected = new java.util.HashSet<>(List.of("coherence_cohesion", "lexical_resource", "grammatical_range_accuracy"));
        expected.add("task_2".equals(evaluation.taskType()) ? "task_response" : "task_achievement");
        if (request.criteria() == null || !request.criteria().keySet().equals(expected) || request.criteria().keySet().stream().anyMatch(key -> !CRITERIA.contains(key))) {
            throw new BusinessRuleException("Bài Writing phải được chấm đủ đúng bốn tiêu chí IELTS");
        }
        if (request.criteria().values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessRuleException("Thiếu điểm tiêu chí Writing");
        }
        validateTeacherEdit(request.editedText(), request.editedHtml(), request.annotations());
        String editedHtml = blankToNull(HtmlSanitizer.sanitizeWritingReview(request.editedHtml()));
        BigDecimal calculatedOverall = WritingBandScorePolicy.taskBand(request.criteria().values().stream()
                .map(ReviewWritingEvaluationRequest.CriterionReview::band).toList());
        if (request.overallBand().compareTo(calculatedOverall) != 0) {
            throw new BusinessRuleException("Overall band phải là trung bình bốn tiêu chí, làm tròn đến 0.5: " + calculatedOverall);
        }
        request.criteria().forEach((criterion, value) -> {
            validateBand(value.band());
            jdbc.update("""
                    insert into public.writing_criterion_scores(evaluation_id, criterion, teacher_band,
                      teacher_feedback, evidence)
                    values (?, ?, ?, ?, '[]'::jsonb)
                    on conflict (evaluation_id, criterion) do update set teacher_band=excluded.teacher_band,
                      teacher_feedback=excluded.teacher_feedback
                    """, id, criterion, value.band(), blankToNull(value.feedback()));
        });
        if ("PUBLISHED".equals(evaluation.teacherStatus()) && !request.publish()) {
            throw new ConflictException("Bài đã công bố phải được lưu và công bố lại khi chỉnh sửa");
        }
        var sanitizedRequest = new ReviewWritingEvaluationRequest(request.overallBand(), request.criteria(),
                request.strengths(), request.improvements(), request.publish(), request.editedText(), editedHtml,
                request.annotations());
        jdbc.update("insert into public.writing_evaluation_reviews(evaluation_id, reviewer_id, review) values (?, ?, cast(? as jsonb))",
                id, reviewerId, json(sanitizedRequest));
        String nextStatus = request.publish() ? "PUBLISHED" : "REVIEWED";
        jdbc.update("""
                update public.writing_evaluations set overall_band_teacher=?, strengths=cast(? as jsonb),
                  improvements=cast(? as jsonb), teacher_status=?, reviewed_by=?,
                  reviewed_at=now(), published_at=case when ? then now() else null end,
                  teacher_edited_text=coalesce(?, coalesce(teacher_edited_text, essay_text)),
                  teacher_edited_html=coalesce(?, teacher_edited_html),
                  teacher_annotations=cast(? as jsonb), teacher_revision=teacher_revision+1,
                  teacher_published_text=case when ? then coalesce(?, coalesce(teacher_edited_text, essay_text)) else teacher_published_text end,
                  teacher_published_html=case when ? then coalesce(?, teacher_edited_html) else teacher_published_html end,
                  teacher_published_annotations=case when ? then cast(? as jsonb) else teacher_published_annotations end,
                  updated_at=now()
                where id=?
                """, request.overallBand(), json(safe(request.strengths())), json(safe(request.improvements())),
                nextStatus, reviewerId, request.publish(), blankToNull(request.editedText()),
                editedHtml, json(request.annotations() == null ? List.of() : request.annotations()), request.publish(),
                blankToNull(request.editedText()), request.publish(), editedHtml, request.publish(),
                json(request.annotations() == null ? List.of() : request.annotations()), id);
        if (request.publish()) notifications.completed(id, true);
        return get(id, reviewerId, globalScope);
    }

    @Transactional
    public WritingEvaluationResponse saveDraft(UUID id, WritingTeacherDraftRequest request, UUID reviewerId) {
        return saveDraft(id, request, reviewerId, true);
    }

    @Transactional
    public WritingEvaluationResponse saveDraft(UUID id, WritingTeacherDraftRequest request, UUID reviewerId,
                                               boolean globalScope) {
        jdbc.queryForList("select id from public.writing_evaluations where id=? for update", id);
        get(id, reviewerId, globalScope);
        validateTeacherEdit(request.editedText(), request.editedHtml(), request.annotations());
        String editedHtml = blankToNull(HtmlSanitizer.sanitizeWritingReview(request.editedHtml()));
        jdbc.update("""
                update public.writing_evaluations
                set teacher_edited_text=coalesce(?, coalesce(teacher_edited_text, essay_text)),
                    teacher_edited_html=coalesce(?, teacher_edited_html),
                    teacher_annotations=cast(? as jsonb), teacher_revision=teacher_revision+1,
                    teacher_status=case when teacher_status='PUBLISHED' then teacher_status else 'REVIEWED' end,
                    reviewed_by=?, reviewed_at=coalesce(reviewed_at, now()), updated_at=now()
                where id=?
                """, blankToNull(request.editedText()), editedHtml,
                json(request.annotations() == null ? List.of() : request.annotations()), reviewerId, id);
        return get(id, reviewerId, globalScope);
    }

    public List<WritingAiEntitlementResponse> listEntitlements(String query) {
        String filter = query == null ? "" : query.trim().toLowerCase();
        return jdbc.query("""
                select student.user_id, student.student_code,
                  coalesce(profile.full_name, profile.email, 'Học viên') student_name, profile.email,
                  entitlement.enabled, entitlement.unlimited, entitlement.monthly_limit,
                  entitlement.active_from, entitlement.expires_at, entitlement.source, entitlement.note,
                  (select count(*) from public.writing_evaluations evaluation
                   join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                   join public.test_attempts attempt on attempt.id=response.attempt_id
                   where attempt.student_id=student.user_id
                     and evaluation.status <> 'FAILED'
                     and evaluation.ai_requested_at >= date_trunc('month', now())) used_this_month
                from public.student_profiles student
                join public.profiles profile on profile.id=student.user_id
                left join public.writing_ai_entitlements entitlement on entitlement.student_id=student.user_id
                where (?='' or lower(coalesce(profile.full_name, '')) like '%' || ? || '%'
                  or lower(coalesce(profile.email, '')) like '%' || ? || '%'
                  or lower(student.student_code) like '%' || ? || '%'
                  or cast(student.user_id as text)=?)
                order by entitlement.enabled desc nulls last, profile.full_name, student.student_code
                limit 500
                """, (rs, ignored) -> entitlement(rs), filter, filter, filter, filter, filter);
    }

    public WritingCalibrationResponse calibration() {
        var overall = jdbc.queryForObject("""
                select count(*) sample_size,
                  round(avg(abs(overall_band_ai - overall_band_teacher)), 3) mae,
                  round(avg(case when abs(overall_band_ai - overall_band_teacher) <= 0.5 then 100.0 else 0 end), 1) within_half,
                  round(avg(case when abs(overall_band_ai - overall_band_teacher) <= 1.0 then 100.0 else 0 end), 1) within_one
                from public.writing_evaluations
                where status='AI_COMPLETED' and teacher_status='PUBLISHED'
                  and overall_band_ai is not null and overall_band_teacher is not null
                """, (rs, ignored) -> new CalibrationMetrics(rs.getLong("sample_size"), rs.getBigDecimal("mae"),
                rs.getBigDecimal("within_half"), rs.getBigDecimal("within_one")));
        var criteria = new LinkedHashMap<String, WritingCalibrationResponse.Criterion>();
        jdbc.query("""
                select criterion, count(*) sample_size,
                  round(avg(abs(ai_band - teacher_band)), 3) mae,
                  round(avg(case when abs(ai_band - teacher_band) <= 0.5 then 100.0 else 0 end), 1) within_half,
                  round(avg(case when abs(ai_band - teacher_band) <= 1.0 then 100.0 else 0 end), 1) within_one
                from public.writing_criterion_scores score
                join public.writing_evaluations evaluation on evaluation.id=score.evaluation_id
                where evaluation.status='AI_COMPLETED' and evaluation.teacher_status='PUBLISHED'
                  and ai_band is not null and teacher_band is not null
                group by criterion order by criterion
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> criteria.put(rs.getString("criterion"),
                new WritingCalibrationResponse.Criterion(rs.getLong("sample_size"), rs.getBigDecimal("mae"),
                        rs.getBigDecimal("within_half"), rs.getBigDecimal("within_one"))));
        CalibrationMetrics value = overall == null ? new CalibrationMetrics(0, null, null, null) : overall;
        return new WritingCalibrationResponse("ielts-writing-public-may-2023-v1", value.sampleSize(),
                value.mae(), value.withinHalf(), value.withinOne(), criteria);
    }

    @Transactional
    public WritingAiEntitlementResponse updateEntitlement(UUID studentId,
                                                           UpdateWritingAiEntitlementRequest request,
                                                           UUID grantedBy) {
        var mode = request.mode();
        if (mode == UpdateWritingAiEntitlementRequest.Mode.LIMITED && request.monthlyLimit() == null) {
            throw new BusinessRuleException("Chế độ giới hạn cần số lượt chấm mỗi tháng");
        }
        OffsetDateTime activeFrom = request.activeFrom() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.activeFrom();
        OffsetDateTime expiresAt = mode == UpdateWritingAiEntitlementRequest.Mode.DISABLED ? null : request.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(activeFrom)) {
            throw new BusinessRuleException("Ngày hết hạn phải sau ngày bắt đầu");
        }
        Long studentCount = jdbc.queryForObject("select count(*) from public.student_profiles where user_id=?", Long.class, studentId);
        if (studentCount == null || studentCount == 0) {
            throw new ResourceNotFoundException("Không tìm thấy học viên");
        }
        Integer limit = mode == UpdateWritingAiEntitlementRequest.Mode.LIMITED ? request.monthlyLimit() : null;
        jdbc.update("""
                insert into public.writing_ai_entitlements(student_id, enabled, unlimited, monthly_limit,
                  active_from, expires_at, source, note, granted_by)
                values (?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?)
                on conflict (student_id) do update set enabled=excluded.enabled, unlimited=excluded.unlimited,
                  monthly_limit=excluded.monthly_limit, active_from=excluded.active_from,
                  expires_at=excluded.expires_at, source='MANUAL', note=excluded.note,
                  granted_by=excluded.granted_by, updated_at=now()
                """, studentId, mode != UpdateWritingAiEntitlementRequest.Mode.DISABLED,
                mode == UpdateWritingAiEntitlementRequest.Mode.UNLIMITED, limit, activeFrom,
                expiresAt, blankToNull(request.note()), grantedBy);
        if (mode == UpdateWritingAiEntitlementRequest.Mode.DISABLED) {
            jdbc.update("""
                    update public.writing_evaluations evaluation set status='NOT_ELIGIBLE', updated_at=now()
                    from public.test_attempt_responses response, public.test_attempts attempt
                    where evaluation.test_attempt_response_id=response.id and response.attempt_id=attempt.id
                      and attempt.student_id=? and evaluation.status='QUEUED'
                    """, studentId);
        }
        return listEntitlements(studentId.toString()).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học viên"));
    }

    private String selectSql() {
        return """
                select evaluation.id, attempt.id attempt_id, attempt.student_id,
                  coalesce(profile.full_name, profile.email, 'Học viên') student_name, student.student_code,
                  version.title test_title, attempt.attempt_origin, course.code course_code,
                  course.name course_name, course.skill_pair::text course_skill_pair,
                  response.question_key task_key, evaluation.task_type,
                  evaluation.essay_text, evaluation.status::text status, evaluation.teacher_status,
                  evaluation.overall_band_ai,
                  evaluation.overall_band_teacher, evaluation.strengths::text strengths,
                  evaluation.improvements::text improvements, evaluation.ai_strengths::text ai_strengths,
                  evaluation.ai_improvements::text ai_improvements, evaluation.error_message,
                  evaluation.ai_completed_at,
                  evaluation.reviewed_at, evaluation.published_at, evaluation.created_at,
                  evaluation.task_context::text task_context,
                  evaluation.teacher_edited_text, evaluation.teacher_edited_html,
                  evaluation.teacher_annotations::text teacher_annotations,
                  evaluation.teacher_revision
                """ + fromSql();
    }

    private String fromSql() {
        return """
                from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                join public.test_attempts attempt on attempt.id=response.attempt_id
                join public.test_versions version on version.id=attempt.test_version_id
                join public.student_profiles student on student.user_id=attempt.student_id
                left join public.test_assignments assignment on assignment.id=attempt.test_assignment_id
                left join public.courses course on course.id=assignment.course_id
                left join public.profiles profile on profile.id=attempt.student_id
                """;
    }

    private WritingEvaluationResponse map(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new WritingEvaluationResponse(id, rs.getObject("attempt_id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getString("student_name"), rs.getString("student_code"), rs.getString("test_title"),
                rs.getString("attempt_origin"), rs.getString("course_code"), rs.getString("course_name"),
                rs.getString("course_skill_pair"), rs.getString("task_key"), rs.getString("task_type"), rs.getString("essay_text"),
                rs.getString("status"), rs.getString("teacher_status"), rs.getBigDecimal("overall_band_ai"),
                rs.getBigDecimal("overall_band_teacher"), criteria(id), strings(rs.getString("ai_strengths")),
                strings(rs.getString("ai_improvements")), strings(rs.getString("strengths")),
                strings(rs.getString("improvements")), rs.getString("error_message"),
                rs.getObject("ai_completed_at", java.time.OffsetDateTime.class),
                rs.getObject("reviewed_at", java.time.OffsetDateTime.class),
                rs.getObject("published_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class), context(rs.getString("task_context")),
                rs.getString("teacher_edited_text"), rs.getString("teacher_edited_html"),
                annotations(rs.getString("teacher_annotations")),
                rs.getObject("teacher_revision", Integer.class));
    }

    private boolean mayUseAi(UUID studentId, int requestedCount) {
        var rows = jdbc.query("""
                select unlimited, monthly_limit from public.writing_ai_entitlements
                where student_id=? and enabled and active_from <= now()
                  and (expires_at is null or expires_at > now())
                """, (rs, ignored) -> new Entitlement(rs.getBoolean("unlimited"),
                rs.getObject("monthly_limit", Integer.class)), studentId);
        if (rows.isEmpty()) return false;
        var entitlement = rows.getFirst();
        if (entitlement.unlimited()) return true;
        Long used = jdbc.queryForObject("""
                select count(*) from public.writing_evaluations evaluation
                join public.test_attempt_responses response on response.id=evaluation.test_attempt_response_id
                join public.test_attempts attempt on attempt.id=response.attempt_id
                where attempt.student_id=?
                  and evaluation.status <> 'FAILED'
                  and evaluation.ai_requested_at >= date_trunc('month', now())
                """, Long.class, studentId);
        return used != null && entitlement.monthlyLimit() != null
                && used + requestedCount <= entitlement.monthlyLimit();
    }

    private boolean hasActiveEntitlement(UUID studentId) {
        Boolean active = jdbc.queryForObject("""
                select exists(select 1 from public.writing_ai_entitlements
                  where student_id=? and enabled and active_from <= now()
                    and (expires_at is null or expires_at > now()))
                """, Boolean.class, studentId);
        return Boolean.TRUE.equals(active);
    }

    private WritingAiEntitlementResponse entitlement(java.sql.ResultSet rs) throws java.sql.SQLException {
        boolean enabled = rs.getObject("enabled") != null && rs.getBoolean("enabled");
        boolean unlimited = enabled && rs.getBoolean("unlimited");
        String mode = !enabled ? "DISABLED" : unlimited ? "UNLIMITED" : "LIMITED";
        return new WritingAiEntitlementResponse(rs.getObject("user_id", UUID.class), rs.getString("student_code"),
                rs.getString("student_name"), rs.getString("email"), mode,
                rs.getObject("monthly_limit", Integer.class), rs.getLong("used_this_month"),
                rs.getObject("active_from", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getString("source"), rs.getString("note"));
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
    private Map<String, Object> context(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Invalid stored Writing context", exception); }
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(); }
    private long number(Object value) { return value instanceof Number number ? number.longValue() : 0; }
    private List<String> strings(String value) { try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ignored) { return List.of(); } }
    private List<WritingTeacherAnnotation> annotations(String value) { try { return objectMapper.readValue(value, new TypeReference<>() {}); } catch (Exception ignored) { return List.of(); } }
    private void validateTeacherEdit(String editedText, String editedHtml, List<WritingTeacherAnnotation> values) {
        if (editedText != null && editedText.length() > 50_000) throw new BusinessRuleException("Bản sửa Writing quá dài");
        if (editedHtml != null && editedHtml.length() > 200_000) throw new BusinessRuleException("Định dạng bản sửa Writing quá dài");
        if (values == null) return;
        for (var value : values) {
            if (value == null || value.start() == null || value.end() == null || value.start() < 0 || value.end() < value.start()) {
                throw new BusinessRuleException("Vị trí annotation Writing không hợp lệ");
            }
            if (value.criterion() != null && !CRITERIA.contains(value.criterion())) {
                throw new BusinessRuleException("Tiêu chí annotation Writing không hợp lệ");
            }
            if (value.kind() != null && !Set.of("COMMENT", "REPLACE", "HIGHLIGHT").contains(value.kind())) {
                throw new BusinessRuleException("Loại annotation Writing không hợp lệ");
            }
        }
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new BusinessRuleException("Không thể lưu nhận xét Writing"); } }
    private record Entitlement(boolean unlimited, Integer monthlyLimit) { }
    private record CalibrationMetrics(long sampleSize, BigDecimal mae, BigDecimal withinHalf, BigDecimal withinOne) { }
}
