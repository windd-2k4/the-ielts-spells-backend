package com.theieltsspells.assignment.application;

import com.theieltsspells.academic.application.AcademicMembershipService;
import com.theieltsspells.assignment.application.dto.*;
import com.theieltsspells.notification.WritingNotificationService;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.ReviewStatus;
import com.theieltsspells.shared.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentApplicationService {

    private static final long MAX_AUDIO_BYTES = 50 * 1024 * 1024;
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav");

    private final JdbcTemplate jdbc;
    private final AcademicMembershipService memberships;
    private final FileStorage storage;
    private final WritingNotificationService notifications;

    @Transactional
    public AssignmentResponse create(UUID courseId, UUID classActivityId, CreateAssignmentRequest request,
                                     UUID actorId, boolean moderator) {
        if (!moderator && !memberships.isTeacherAssigned(courseId, actorId)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        var activity = activity(courseId, classActivityId);
        validateSchedule(request.opensAt(), request.dueAt());
        String latePolicy = normalizedLatePolicy(request.latePolicy());
        int lateGraceMinutes = normalizedGraceMinutes(request.lateGraceMinutes());
        var maxScore = AssignmentSubmissionPolicy.maxScore(activity.skill(), request.maxScore());
        validateReflectionDeadline(request.reflectionDueAt(), request.dueAt());
        if (activity.assignmentId() != null) return assignment(activity.assignmentId());

        String type = normalizedType(request.assignmentType(), activity.skill());
        UUID assignmentId = jdbc.queryForObject("""
                insert into public.assignments(
                  course_id, created_by, title, description, assignment_type,
                  opens_at, due_at, max_score, allow_resubmission,
                  late_policy, late_grace_minutes, reflection_due_at, status
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED'::public.assignment_status)
                returning id
                """, UUID.class, courseId, actorId,
                blank(request.title()) == null ? activity.title() : blank(request.title()),
                blank(request.description()), type, request.opensAt(), request.dueAt(),
                maxScore, request.allowResubmission() == null || request.allowResubmission(),
                latePolicy, lateGraceMinutes, request.reflectionDueAt());

        jdbc.update("""
                update public.course_activities
                set assignment_id = ?, is_published = true, updated_at = now()
                where id = ? and course_id = ?
                """, assignmentId, classActivityId, courseId);
        notifications.assignmentPublished(assignmentId, courseId, classActivityId,
                blank(request.title()) == null ? activity.title() : blank(request.title()), activity.skill());
        return assignment(assignmentId);
    }

    @Transactional
    public AssignmentResponse assignmentForActivity(UUID courseId, UUID classActivityId) {
        var activity = activity(courseId, classActivityId);
        if (activity.assignmentId() == null) {
            throw new ResourceNotFoundException("Hoạt động này chưa có assignment");
        }
        return assignment(activity.assignmentId());
    }

    @Transactional
    public AssignmentResponse updateSettings(UUID courseId, UUID classActivityId,
                                             UpdateAssignmentSettingsRequest request,
                                             UUID actorId, boolean moderator) {
        if (!moderator && !memberships.isTeacherAssigned(courseId, actorId)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        var activity = activity(courseId, classActivityId);
        if (activity.assignmentId() == null) {
            throw new ResourceNotFoundException("Hoạt động này chưa có assignment");
        }
        validateSchedule(request.opensAt(), request.dueAt());
        validateReflectionDeadline(request.reflectionDueAt(), request.dueAt());
        String latePolicy = normalizedLatePolicy(request.latePolicy());
        int lateGraceMinutes = normalizedGraceMinutes(request.lateGraceMinutes());
        jdbc.update("""
                update public.assignments
                set opens_at = ?, due_at = ?, allow_resubmission = ?,
                    late_policy = ?, late_grace_minutes = ?, reflection_due_at = ?, updated_at = now()
                where id = ? and course_id = ?
                """, request.opensAt(), request.dueAt(),
                request.allowResubmission() == null || request.allowResubmission(),
                latePolicy, lateGraceMinutes, request.reflectionDueAt(), activity.assignmentId(), courseId);
        return assignment(activity.assignmentId());
    }

    public AssignmentStudentExtensionResponse assignmentStudentExtension(UUID courseId, UUID classActivityId,
                                                                          UUID studentId) {
        var activity = activity(courseId, classActivityId);
        if (activity.assignmentId() == null) {
            throw new ResourceNotFoundException("Hoạt động này chưa có assignment");
        }
        requireActiveEnrollment(studentId, courseId);
        var values = jdbc.query("""
                select extension_due_at, reason
                from public.assignment_student_extensions
                where assignment_id = ? and class_activity_id = ? and student_id = ?
                """, (rs, row) -> new AssignmentStudentExtensionResponse(
                studentId, rs.getObject("extension_due_at", OffsetDateTime.class), rs.getString("reason")),
                activity.assignmentId(), classActivityId, studentId);
        return values.isEmpty() ? new AssignmentStudentExtensionResponse(studentId, null, null) : values.getFirst();
    }

    @Transactional
    public AssignmentStudentExtensionResponse grantAssignmentStudentExtension(
            UUID courseId, UUID classActivityId, UUID studentId, GrantSubmissionExtensionRequest request,
            UUID actorId, boolean moderator) {
        if (!moderator && !memberships.isTeacherAssigned(courseId, actorId)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        var activity = activity(courseId, classActivityId);
        if (activity.assignmentId() == null) {
            throw new ResourceNotFoundException("Hoạt động này chưa có assignment");
        }
        requireActiveEnrollment(studentId, courseId);
        if (request.extensionDueAt() == null || !request.extensionDueAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessRuleException("Extension deadline phải ở tương lai");
        }
        var assignment = assignmentContext(activity.assignmentId());
        if (assignment != null && assignment.dueAt() != null && !request.extensionDueAt().isAfter(assignment.dueAt())) {
            throw new BusinessRuleException("Extension deadline phải sau deadline gốc của assignment");
        }
        jdbc.update("""
                insert into public.assignment_student_extensions(
                  assignment_id, class_activity_id, student_id, extension_due_at, reason, granted_by
                ) values (?, ?, ?, ?, ?, ?)
                on conflict (assignment_id, student_id) do update set
                  class_activity_id = excluded.class_activity_id,
                  extension_due_at = excluded.extension_due_at,
                  reason = excluded.reason,
                  granted_by = excluded.granted_by,
                  updated_at = now()
                """, activity.assignmentId(), classActivityId, studentId, request.extensionDueAt(),
                blank(request.reason()), actorId);
        notifications.assignmentExtensionGranted(studentId, activity.assignmentId(), courseId,
                classActivityId, request.extensionDueAt());
        return new AssignmentStudentExtensionResponse(studentId, request.extensionDueAt(), blank(request.reason()));
    }

    public List<StudentSubmissionResponse> submissions(UUID courseId, UUID classActivityId) {
        activity(courseId, classActivityId);
        return jdbc.query("""
                with ranked as (
                  select submission.id, submission.assignment_id, activity.id class_activity_id,
                    submission.student_id, profile.full_name, submission.attempt_no,
                    submission.text_content, submission.status::text status, submission.submitted_at,
                    submission.is_late, submission.score, attempt.review_status::text review_status,
                    attempt.reflection, review.id feedback_id, review.feedback, review.reviewed_at,
                    case
                      when submission.extension_due_at is null then student_extension.extension_due_at
                      when student_extension.extension_due_at is null then submission.extension_due_at
                      when submission.extension_due_at > student_extension.extension_due_at then submission.extension_due_at
                      else student_extension.extension_due_at
                    end extension_due_at,
                    coalesce(submission.extension_reason, student_extension.reason) extension_reason,
                    review.reflection_due_at,
                    audio.id audio_id,
                    row_number() over (
                      partition by activity.id, submission.student_id
                      order by submission.attempt_no desc
                    ) latest_attempt
                  from public.submissions submission
                  join public.course_activities activity on activity.assignment_id = submission.assignment_id
                    and activity.id = ? and activity.course_id = ?
                  join public.profiles profile on profile.id = submission.student_id
                  left join public.assignment_student_extensions student_extension
                    on student_extension.assignment_id = submission.assignment_id
                   and student_extension.student_id = submission.student_id
                  left join public.student_activity_attempts attempt on attempt.submission_id = submission.id
                  left join lateral (
                    select teacher_review.id, teacher_review.feedback, teacher_review.reviewed_at,
                      coalesce(teacher_review.reflection_due_at, assignment.reflection_due_at) reflection_due_at
                    from public.teacher_activity_reviews teacher_review
                    join public.assignments assignment on assignment.id = submission.assignment_id
                    where teacher_review.attempt_id = attempt.id
                    order by teacher_review.reviewed_at desc
                    limit 1
                  ) review on true
                  left join lateral (
                    select submission_file.id
                    from public.submission_files submission_file
                    where submission_file.submission_id = submission.id
                      and lower(submission_file.mime_type) like 'audio/%'
                    order by submission_file.uploaded_at desc
                    limit 1
                  ) audio on true
                )
                select id, assignment_id, class_activity_id, student_id, full_name, attempt_no,
                  text_content, status, submitted_at, is_late, score, review_status, reflection,
                  feedback_id, feedback, reviewed_at, extension_due_at, extension_reason, reflection_due_at, audio_id
                from ranked
                where latest_attempt = 1
                order by student_id, attempt_no desc
                """, (rs, row) -> submissionResponse(courseId,
                rs.getObject("id", UUID.class), rs.getObject("assignment_id", UUID.class),
                rs.getObject("class_activity_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("full_name"), rs.getShort("attempt_no"), rs.getString("text_content"),
                upper(rs.getString("status")), rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getBoolean("is_late"), rs.getBigDecimal("score"), upper(rs.getString("review_status")),
                rs.getString("reflection"), rs.getObject("feedback_id", UUID.class),
                 rs.getString("feedback"), rs.getObject("reviewed_at", OffsetDateTime.class),
                 rs.getObject("extension_due_at", OffsetDateTime.class),
                 rs.getString("extension_reason"),
                 rs.getObject("reflection_due_at", OffsetDateTime.class),
                 rs.getObject("audio_id", UUID.class) == null ? null
                         : adminAudioUrl(courseId, classActivityId, rs.getObject("id", UUID.class))
        ), classActivityId, courseId);
    }

    public List<StudentSubmissionRevisionResponse> revisions(UUID studentId, UUID courseId, UUID classActivityId) {
        activity(courseId, classActivityId);
        requireEnrollment(studentId, courseId);
        return jdbc.query("""
                select submission.id submission_id, attempt.id attempt_id, submission.attempt_no,
                  submission.status::text status, submission.text_content, submission.submitted_at, submission.score,
                  attempt.review_status::text review_status, attempt.reflection,
                  review.feedback, review.reviewed_at, submission.extension_due_at,
                  coalesce(review.reflection_due_at, assignment.reflection_due_at) reflection_due_at,
                  exists (select 1 from public.submission_files file
                          where file.submission_id = submission.id
                            and lower(file.mime_type) like 'audio/%') audio_submitted
                from public.submissions submission
                join public.assignments assignment on assignment.id = submission.assignment_id
                join public.course_activities activity on activity.assignment_id = submission.assignment_id
                  and activity.id = ? and activity.course_id = ?
                join public.student_activity_attempts attempt on attempt.submission_id = submission.id
                left join lateral (
                  select teacher_review.feedback, teacher_review.reviewed_at,
                    teacher_review.reflection_due_at
                  from public.teacher_activity_reviews teacher_review
                  where teacher_review.attempt_id = attempt.id
                  order by teacher_review.reviewed_at desc limit 1
                ) review on true
                where submission.student_id = ?
                order by submission.attempt_no desc
                """, (rs, row) -> new StudentSubmissionRevisionResponse(
                rs.getObject("submission_id", UUID.class),
                rs.getObject("attempt_id", UUID.class),
                rs.getShort("attempt_no"),
                upper(rs.getString("status")),
                rs.getString("text_content"),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getBigDecimal("score"),
                upper(rs.getString("review_status")),
                rs.getString("reflection"),
                rs.getString("feedback"),
                rs.getObject("reviewed_at", OffsetDateTime.class),
                rs.getBoolean("audio_submitted"),
                rs.getObject("extension_due_at", OffsetDateTime.class),
                rs.getObject("reflection_due_at", OffsetDateTime.class)
        ), classActivityId, courseId, studentId);
    }

    @Transactional
    public StudentSubmissionResponse saveStudentSubmission(UUID studentId, UUID courseId, UUID classActivityId,
                                                            StudentSubmissionRequest request) {
        var context = prepareSubmission(studentId, courseId, classActivityId, request.textContent());
        if (request.submit()) completeSubmission(context);
        return submission(studentId, courseId, context.submissionId());
    }

    @Transactional
    public StudentSubmissionResponse saveStudentAudioSubmission(UUID studentId, UUID courseId,
                                                                 UUID classActivityId, MultipartFile file,
                                                                 String requestedPromptKey,
                                                                 String requestedPromptTitle,
                                                                 int sortOrder,
                                                                 boolean submit) {
        validateAudio(file);
        var activity = activity(courseId, classActivityId);
        if (!"SPEAKING".equalsIgnoreCase(activity.skill())) {
            throw new BusinessRuleException("Audio chỉ được nộp cho hoạt động Speaking");
        }
        var context = prepareSubmission(studentId, courseId, classActivityId, null);
        String promptKey = normalizedPromptKey(requestedPromptKey);
        String promptTitle = blank(requestedPromptTitle);
        if (promptTitle == null) promptTitle = "Bài nói " + Math.max(1, sortOrder + 1);
        if (promptTitle.length() > 200) throw new BusinessRuleException("Tên phần Speaking không được vượt quá 200 ký tự");
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String objectPath = "assignments/submissions/" + studentId + "/" + context.submissionId()
                + "/" + promptKey + audioExtension(contentType);
        String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "speaking-audio" + audioExtension(contentType)
                : file.getOriginalFilename();
        storage.store(objectPath, file);
        jdbc.update("delete from public.submission_files where submission_id = ? and prompt_key = ?",
                context.submissionId(), promptKey);
        jdbc.update("""
                insert into public.submission_files(
                  submission_id, storage_path, original_name, mime_type, size_bytes,
                  prompt_key, prompt_title, sort_order, uploaded_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """, context.submissionId(), objectPath, originalName, contentType, file.getSize(),
                promptKey, promptTitle, Math.max(0, sortOrder));
        if (submit) completeSubmission(context);
        return submission(studentId, courseId, context.submissionId());
    }

    private SubmissionContext prepareSubmission(UUID studentId, UUID courseId, UUID classActivityId,
                                                String textContent) {
        var activity = activity(courseId, classActivityId);
        requireEnrollment(studentId, courseId);
        var assignment = assignmentContext(activity.assignmentId());
        if (assignment == null) throw new ResourceNotFoundException("Hoạt động này chưa có assignment");
        if (!"PUBLISHED".equalsIgnoreCase(assignment.status())) {
            throw new BusinessRuleException("Assignment chưa được mở cho học viên");
        }
        if (assignment.opensAt() != null && OffsetDateTime.now().isBefore(assignment.opensAt())) {
            throw new BusinessRuleException("Assignment chưa đến thời điểm mở");
        }

        var latest = latestAttempt(classActivityId, studentId);
        OffsetDateTime extensionDueAt = later(latest == null ? null : latest.extensionDueAt(),
                assignmentStudentExtension(assignment.id(), studentId));
        OffsetDateTime effectiveDueAt = AssignmentSubmissionPolicy.effectiveDueAt(
                assignment.dueAt(), assignment.lateGraceMinutes(), extensionDueAt);
        if (effectiveDueAt != null && OffsetDateTime.now().isAfter(effectiveDueAt)
                && "BLOCK".equals(assignment.latePolicy())) {
            throw new BusinessRuleException("Đã quá hạn nộp bài và assignment đang chặn bài nộp muộn");
        }

        boolean revision = latest != null && List.of("REVISION_REQUESTED", "REJECTED")
                .contains(upper(latest.reviewStatus()));
        if (revision && !assignment.allowResubmission()) {
            throw new BusinessRuleException("Assignment không cho phép nộp lại");
        }
        if (latest != null && !revision && !List.of("DRAFT", "IN_PROGRESS").contains(upper(latest.status()))) {
            throw new BusinessRuleException("Hãy chờ giáo viên phản hồi trước khi nộp lại");
        }

        UUID submissionId;
        UUID attemptId;
        short attemptNo;
        if (latest == null || revision) {
            attemptNo = latest == null ? 1 : (short) (latest.attemptNo() + 1);
            submissionId = UUID.randomUUID();
            attemptId = UUID.randomUUID();
            jdbc.update("""
                    insert into public.submissions(
                      id, assignment_id, student_id, attempt_no, text_content, status, is_late,
                      created_at, updated_at
                    ) values (?, ?, ?, ?, ?, 'DRAFT'::public.submission_status, false, now(), now())
                    """, submissionId, assignment.id(), studentId, attemptNo, textContent);
            jdbc.update("""
                    insert into public.student_activity_attempts(
                      id, class_activity_id, student_id, attempt_no, source, status, review_status,
                      submission_id, max_score, reflection, is_late, metadata, created_at, updated_at
                    ) values (?, ?, ?, ?, 'WEB'::public.result_source, 'IN_PROGRESS'::public.activity_attempt_status,
                      'PENDING'::public.review_status, ?, ?, ?, false, '{}'::jsonb, now(), now())
                    """, attemptId, classActivityId, studentId, attemptNo, submissionId, assignment.maxScore(),
                    latest == null ? null : latest.reflection());
        } else {
            submissionId = latest.submissionId();
            attemptId = latest.attemptId();
            attemptNo = latest.attemptNo();
            if (textContent != null) {
                jdbc.update("update public.submissions set text_content = ?, updated_at = now() where id = ? and student_id = ?",
                        textContent, submissionId, studentId);
            }
        }
        return new SubmissionContext(activity, assignment, submissionId, attemptId, attemptNo, extensionDueAt);
    }

    private void completeSubmission(SubmissionContext context) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime effectiveDue = AssignmentSubmissionPolicy.effectiveDueAt(
                context.assignment().dueAt(), context.assignment().lateGraceMinutes(), context.extensionDueAt());
        boolean pastDue = effectiveDue != null && now.isAfter(effectiveDue);
        if (pastDue && "BLOCK".equals(context.assignment().latePolicy())) {
            throw new BusinessRuleException("Đã quá hạn nộp bài và assignment đang chặn bài nộp muộn");
        }
        boolean late = pastDue && "MARK_LATE".equals(context.assignment().latePolicy());
        String submissionStatus = late ? "LATE" : "SUBMITTED";
        String attemptStatus = context.activity().requiresTeacherReview() ? "WAITING_REVIEW" : "COMPLETED";
        String reviewStatus = context.activity().requiresTeacherReview() ? "PENDING" : "NOT_REQUIRED";
        jdbc.update("""
                update public.submissions
                set status = ?::public.submission_status, submitted_at = now(), is_late = ?, updated_at = now()
                where id = ?
                """, submissionStatus, late, context.submissionId());
        jdbc.update("""
                update public.student_activity_attempts
                set status = ?::public.activity_attempt_status,
                    review_status = ?::public.review_status,
                    submitted_at = now(), completed_at = case when ? then now() else null end,
                    is_late = ?, updated_at = now()
                where id = ?
                """, attemptStatus, reviewStatus, !context.activity().requiresTeacherReview(), late, context.attemptId());
    }

    public SubmissionAudio openAudio(UUID courseId, UUID classActivityId, UUID submissionId, UUID studentId) {
        return openAudio(courseId, classActivityId, submissionId, null, studentId);
    }

    public SubmissionAudio openAudio(UUID courseId, UUID classActivityId, UUID submissionId,
                                     UUID fileId, UUID studentId) {
        activity(courseId, classActivityId);
        requireEnrollmentIfStudent(studentId, courseId);
        String studentScope = studentId == null ? "" : " and submission.student_id = ?";
        String fileScope = fileId == null ? "" : " and file.id = ?";
        var parameters = new ArrayList<Object>();
        parameters.add(classActivityId);
        parameters.add(courseId);
        parameters.add(submissionId);
        if (fileId != null) parameters.add(fileId);
        if (studentId != null) parameters.add(studentId);
        var values = jdbc.query("""
                select file.storage_path, file.mime_type, file.size_bytes, file.original_name
                from public.submission_files file
                join public.submissions submission on submission.id = file.submission_id
                join public.course_activities activity on activity.assignment_id = submission.assignment_id
                  and activity.id = ? and activity.course_id = ?
                where file.submission_id = ? and lower(file.mime_type) like 'audio/%'
                """ + fileScope + studentScope + """
                order by file.sort_order, file.uploaded_at
                limit 1
                """, (rs, row) -> new SubmissionAudio(
                storage.openDefault(rs.getString("storage_path")), rs.getString("mime_type"),
                rs.getObject("size_bytes", Long.class), rs.getString("original_name")),
                parameters.toArray());
        if (values.isEmpty()) throw new ResourceNotFoundException("Submission chưa có file audio");
        return values.getFirst();
    }

    @Transactional
    public void saveReflection(UUID studentId, UUID courseId, UUID attemptId, ReflectionRequest request) {
        requireEnrollment(studentId, courseId);
        var deadline = jdbc.query("""
                select coalesce(review.reflection_due_at, assignment.reflection_due_at) reflection_due_at
                from public.student_activity_attempts attempt
                join public.submissions submission on submission.id = attempt.submission_id
                join public.assignments assignment on assignment.id = submission.assignment_id
                left join lateral (
                  select teacher_review.reflection_due_at
                  from public.teacher_activity_reviews teacher_review
                  where teacher_review.attempt_id = attempt.id
                  order by teacher_review.reviewed_at desc limit 1
                ) review on true
                where attempt.id = ? and attempt.student_id = ? and assignment.course_id = ?
                """, (rs, row) -> rs.getObject("reflection_due_at", OffsetDateTime.class),
                attemptId, studentId, courseId);
        OffsetDateTime reflectionDueAt = deadline.isEmpty() ? null : deadline.getFirst();
        if (reflectionDueAt != null && OffsetDateTime.now().isAfter(reflectionDueAt)) {
            throw new BusinessRuleException("Đã quá hạn reflection cho submission này");
        }
        int updated = jdbc.update("""
                update public.student_activity_attempts attempt
                set reflection = ?, updated_at = now()
                from public.course_activities activity
                where attempt.id = ? and attempt.student_id = ?
                  and activity.id = attempt.class_activity_id and activity.course_id = ?
                """, blank(request.reflection()), attemptId, studentId, courseId);
        if (updated == 0) throw new ResourceNotFoundException("Không tìm thấy lượt làm bài");
    }

    @Transactional
    public StudentSubmissionResponse grantExtension(UUID courseId, UUID classActivityId, UUID submissionId,
                                                    GrantSubmissionExtensionRequest request, UUID actorId,
                                                    boolean moderator) {
        if (!moderator && !memberships.isTeacherAssigned(courseId, actorId)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        activity(courseId, classActivityId);
        if (request.extensionDueAt() == null || !request.extensionDueAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessRuleException("Extension deadline phải ở tương lai");
        }
        var source = jdbc.query("""
                select submission.student_id, submission.assignment_id
                from public.submissions submission
                join public.course_activities activity on activity.assignment_id = submission.assignment_id
                  and activity.id = ? and activity.course_id = ?
                where submission.id = ?
                  and not exists (
                    select 1 from public.student_activity_attempts newer
                    where newer.class_activity_id = activity.id
                      and newer.student_id = submission.student_id
                      and newer.attempt_no > submission.attempt_no
                  )
                """, (rs, row) -> new ExtensionSource(
                rs.getObject("student_id", UUID.class), rs.getObject("assignment_id", UUID.class)),
                classActivityId, courseId, submissionId);
        if (source.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy submission hiện tại");
        var item = source.getFirst();
        var assignmentDetails = assignmentContext(item.assignmentId());
        if (assignmentDetails != null && assignmentDetails.dueAt() != null
                && !request.extensionDueAt().isAfter(assignmentDetails.dueAt())) {
            throw new BusinessRuleException("Extension deadline phải sau deadline gốc của assignment");
        }
        jdbc.update("""
                update public.submissions
                set extension_due_at = ?, extension_reason = ?, extension_granted_by = ?, updated_at = now()
                where id = ?
                """, request.extensionDueAt(), blank(request.reason()), actorId, submissionId);
        jdbc.update("""
                insert into public.assignment_student_extensions(
                  assignment_id, class_activity_id, student_id, extension_due_at, reason, granted_by
                ) values (?, ?, ?, ?, ?, ?)
                on conflict (assignment_id, student_id) do update set
                  class_activity_id = excluded.class_activity_id,
                  extension_due_at = excluded.extension_due_at,
                  reason = excluded.reason,
                  granted_by = excluded.granted_by,
                  updated_at = now()
                """, item.assignmentId(), classActivityId, item.studentId(), request.extensionDueAt(),
                blank(request.reason()), actorId);
        notifications.assignmentExtensionGranted(item.studentId(), item.assignmentId(), courseId,
                classActivityId, request.extensionDueAt());
        return submission(item.studentId(), courseId, submissionId);
    }

    @Transactional
    public BulkAssignmentReminderResponse sendBulkReminder(UUID courseId, UUID classActivityId,
                                                            BulkAssignmentReminderRequest request, UUID actorId,
                                                            boolean moderator) {
        if (!moderator && !memberships.isTeacherAssigned(courseId, actorId)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        var activity = activity(courseId, classActivityId);
        if (activity.assignmentId() == null) throw new BusinessRuleException("Hoạt động chưa có assignment");
        String channel = normalizedContactChannel(request.channel());
        boolean sendNotification = request.sendNotification() == null || request.sendNotification();
        String title = assignment(activity.assignmentId()).title();
        String message = blank(request.message()) == null
                ? "Bạn có assignment cần hoàn thành: " + title + ". Hãy kiểm tra deadline và nộp bài trên hệ thống."
                : blank(request.message());
        Set<UUID> requested = request.studentIds() == null ? Set.of() : new HashSet<>(request.studentIds());
        var recipients = jdbc.query("""
                select enrollment.student_id
                from public.enrollments enrollment
                where enrollment.course_id = ? and enrollment.status = 'ACTIVE'
                order by enrollment.student_id
                """, (rs, row) -> rs.getObject("student_id", UUID.class), courseId).stream()
                .filter(studentId -> requested.isEmpty() || requested.contains(studentId))
                .toList();
        int notificationsCreated = 0;
        for (UUID studentId : recipients) {
            if (sendNotification && "IN_APP".equals(channel)
                    && notifications.enabled(studentId, "ASSIGNMENT_REMINDER")) {
                jdbc.update("""
                        insert into public.notifications(user_id, type, title, body, data)
                        values (?, 'ASSIGNMENT_REMINDER', ?, ?, jsonb_build_object(
                          'assignmentId', cast(? as text), 'courseId', cast(? as text),
                          'classActivityId', cast(? as text), 'skill', ?))
                        """, studentId, "Nhắc hoàn thành bài: " + title, message,
                        activity.assignmentId(), courseId, classActivityId, activity.skill());
                notificationsCreated++;
            }
            jdbc.update("""
                    insert into public.assignment_contact_logs(
                      course_id, assignment_id, class_activity_id, student_id, actor_id,
                      channel, action, message
                    ) values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, courseId, activity.assignmentId(), classActivityId, studentId, actorId,
                    channel, sendNotification ? "REMINDER_SENT" : "CONTACT_LOGGED", message);
        }
        return new BulkAssignmentReminderResponse(recipients.size(), notificationsCreated, recipients.size());
    }

    public List<AssignmentContactLogResponse> contactLogs(UUID courseId, UUID classActivityId,
                                                          UUID actorId, boolean moderator) {
        if (!moderator && !memberships.isTeacherAssigned(courseId, actorId)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        activity(courseId, classActivityId);
        return jdbc.query("""
                select log.id, log.student_id, student.full_name student_name,
                  log.actor_id, actor.full_name actor_name, log.channel, log.action,
                  log.message, log.created_at
                from public.assignment_contact_logs log
                join public.profiles student on student.id = log.student_id
                join public.profiles actor on actor.id = log.actor_id
                where log.course_id = ? and log.class_activity_id = ?
                order by log.created_at desc
                limit 100
                """, (rs, row) -> new AssignmentContactLogResponse(
                rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("student_name"), rs.getObject("actor_id", UUID.class),
                rs.getString("actor_name"), rs.getString("channel"), rs.getString("action"),
                rs.getString("message"), rs.getObject("created_at", OffsetDateTime.class)),
                courseId, classActivityId);
    }

    @Transactional
    public StudentSubmissionResponse review(UUID courseId, UUID classActivityId, UUID submissionId,
                                            ReviewSubmissionRequest request, UUID reviewerId, boolean moderator) {
        if (!moderator && !memberships.isTeacherAssigned(courseId, reviewerId)) {
            throw new BusinessRuleException("Bạn không được phân công cho khóa học này");
        }
        activity(courseId, classActivityId);
        var source = jdbc.query("""
                select submission.id, submission.assignment_id, submission.student_id, submission.attempt_no,
                  submission.text_content,
                  activity.id class_activity_id, attempt.id attempt_id, assignment.max_score,
                  learning_activity.skill::text skill
                from public.submissions submission
                join public.assignments assignment on assignment.id = submission.assignment_id
                join public.course_activities activity on activity.assignment_id = assignment.id
                  and activity.id = ? and activity.course_id = ?
                join public.learning_activities learning_activity on learning_activity.id = activity.activity_id
                join public.student_activity_attempts attempt on attempt.submission_id = submission.id
                where submission.id = ?
                  and submission.status <> 'DRAFT'::public.submission_status
                  and not exists (
                    select 1 from public.student_activity_attempts newer
                    where newer.class_activity_id = activity.id
                      and newer.student_id = submission.student_id
                      and newer.attempt_no > submission.attempt_no
                  )
                """, (rs, row) -> new ReviewSource(
                rs.getObject("id", UUID.class), rs.getObject("assignment_id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getShort("attempt_no"),
                rs.getObject("class_activity_id", UUID.class), rs.getObject("attempt_id", UUID.class),
                rs.getBigDecimal("max_score"), upper(rs.getString("skill")), rs.getString("text_content")
        ), classActivityId, courseId, submissionId);
        if (source.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy submission");

        ReviewStatus status;
        try {
            status = ReviewStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Trạng thái feedback không hợp lệ");
        }
        if (status == ReviewStatus.PENDING || status == ReviewStatus.NOT_REQUIRED) {
            throw new BusinessRuleException("Feedback phải là VERIFIED, REVISION_REQUESTED hoặc REJECTED");
        }
        if (request.score() != null && source.getFirst().maxScore() != null
                && (request.score().signum() < 0 || request.score().compareTo(source.getFirst().maxScore()) > 0)) {
            throw new BusinessRuleException("Điểm feedback vượt quá điểm tối đa");
        }

        var item = source.getFirst();
        validateStructuredReview(item, request);
        var assignmentDetails = assignment(item.assignmentId());
        validateReflectionDeadline(request.reflectionDueAt(), assignmentDetails.dueAt());
        if (request.reflectionDueAt() != null && !request.reflectionDueAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessRuleException("Deadline reflection phải ở tương lai");
        }
        boolean revision = status == ReviewStatus.REVISION_REQUESTED || status == ReviewStatus.REJECTED;
        UUID reviewId = jdbc.queryForObject("""
                insert into public.teacher_activity_reviews(
                  attempt_id, reviewer_id, status, verified_score, feedback, private_note,
                  adjustment_reason, reflection_due_at, reviewed_at, created_at
                ) values (?, ?, ?::public.review_status, ?, ?, ?, ?, ?, now(), now())
                returning id
                """, UUID.class, item.attemptId(), reviewerId, status.name(), request.score(),
                blank(request.feedback()), blank(request.privateNote()), blank(request.adjustmentReason()),
                request.reflectionDueAt());
        for (var criterion : safeCriteria(request)) {
            jdbc.update("""
                    insert into public.teacher_review_criteria(review_id, criterion, score, feedback, sort_order)
                    values (?, ?, ?, ?, ?)
                    """, reviewId, upper(criterion.criterion()), criterion.score(), blank(criterion.feedback()),
                    Math.max(0, criterion.sortOrder()));
        }
        for (var annotation : safeAnnotations(request)) {
            jdbc.update("""
                    insert into public.teacher_review_annotations(
                      review_id, start_offset, end_offset, quoted_text, comment, suggestion, sort_order
                    ) values (?, ?, ?, ?, ?, ?, ?)
                    """, reviewId, annotation.startOffset(), annotation.endOffset(), annotation.quotedText(),
                    annotation.comment().trim(), blank(annotation.suggestion()), Math.max(0, annotation.sortOrder()));
        }
        jdbc.update("""
                update public.submissions
                set status = ?::public.submission_status, score = ?, teacher_feedback = ?,
                    graded_by = ?, graded_at = now(), updated_at = now()
                where id = ?
                """, revision ? "RETURNED" : "GRADED", request.score(), blank(request.feedback()),
                reviewerId, submissionId);
        jdbc.update("""
                update public.student_activity_attempts
                set status = ?::public.activity_attempt_status,
                    review_status = ?::public.review_status, score = ?,
                    completed_at = case when ? then null else coalesce(completed_at, now()) end,
                    updated_at = now()
                where id = ?
                """, revision ? "NEEDS_REVISION" : "COMPLETED", status.name(), request.score(),
                revision, item.attemptId());
        notifications.assignmentReviewed(item.studentId(), item.assignmentId(), courseId, classActivityId,
                item.attemptId(), assignmentDetails.title(), item.skill(), status.name(), blank(request.feedback()));
        return submission(item.studentId(), courseId, submissionId);
    }

    private AssignmentContext assignmentContext(UUID assignmentId) {
        if (assignmentId == null) return null;
        var values = jdbc.query("""
                select id, course_id, allow_resubmission, opens_at, due_at, max_score, status::text status,
                  late_policy, late_grace_minutes, reflection_due_at
                from public.assignments where id = ?
                """, (rs, row) -> new AssignmentContext(
                rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getBoolean("allow_resubmission"), rs.getObject("opens_at", OffsetDateTime.class),
                rs.getObject("due_at", OffsetDateTime.class), rs.getBigDecimal("max_score"),
                upper(rs.getString("status")), upper(rs.getString("late_policy")),
                rs.getInt("late_grace_minutes"), rs.getObject("reflection_due_at", OffsetDateTime.class)
        ), assignmentId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private ActivityContext activity(UUID courseId, UUID classActivityId) {
        var values = jdbc.query("""
                select ca.id, ca.course_id, ca.session_id, ca.activity_id, ca.assignment_id,
                  la.title, la.skill::text skill, la.requires_teacher_review
                from public.course_activities ca
                join public.learning_activities la on la.id = ca.activity_id
                where ca.id = ? and ca.course_id = ? and ca.is_published = true
                """, (rs, row) -> new ActivityContext(
                rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("activity_id", UUID.class),
                rs.getObject("assignment_id", UUID.class), rs.getString("title"),
                upper(rs.getString("skill")), rs.getBoolean("requires_teacher_review")
        ), classActivityId, courseId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy hoạt động của khóa học");
        return values.getFirst();
    }

    private AssignmentResponse assignment(UUID assignmentId) {
        var values = jdbc.query("""
                select assignment.id, assignment.course_id, activity.session_id, activity.id class_activity_id,
                  activity.activity_id, assignment.title, assignment.description, assignment.assignment_type,
                  assignment.opens_at, assignment.due_at, assignment.max_score,
                  assignment.allow_resubmission, assignment.status::text status,
                  assignment.late_policy, assignment.late_grace_minutes, assignment.reflection_due_at
                from public.assignments assignment
                join public.course_activities activity on activity.assignment_id = assignment.id
                where assignment.id = ?
                """, (rs, row) -> new AssignmentResponse(
                rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("session_id", UUID.class), rs.getObject("class_activity_id", UUID.class),
                rs.getObject("activity_id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getString("assignment_type"), rs.getObject("opens_at", OffsetDateTime.class),
                rs.getObject("due_at", OffsetDateTime.class), rs.getBigDecimal("max_score"),
                rs.getBoolean("allow_resubmission"), upper(rs.getString("status")),
                upper(rs.getString("late_policy")), rs.getInt("late_grace_minutes"),
                rs.getObject("reflection_due_at", OffsetDateTime.class)
        ), assignmentId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy assignment");
        return values.getFirst();
    }

    private StudentSubmissionResponse submission(UUID studentId, UUID courseId, UUID submissionId) {
        var values = jdbc.query("""
                select submission.id, submission.assignment_id, activity.id class_activity_id,
                  submission.student_id, profile.full_name, submission.attempt_no,
                  submission.text_content, submission.status::text status, submission.submitted_at,
                   submission.is_late, submission.score, attempt.review_status::text review_status,
                   attempt.reflection, review.id feedback_id, review.feedback, review.reviewed_at,
                   case
                     when submission.extension_due_at is null then student_extension.extension_due_at
                     when student_extension.extension_due_at is null then submission.extension_due_at
                     when submission.extension_due_at > student_extension.extension_due_at then submission.extension_due_at
                     else student_extension.extension_due_at
                   end extension_due_at,
                   coalesce(submission.extension_reason, student_extension.reason) extension_reason,
                   review.reflection_due_at,
                   audio.id audio_id
                from public.submissions submission
                join public.assignments assignment on assignment.id = submission.assignment_id
                join public.course_activities activity on activity.assignment_id = submission.assignment_id
                join public.profiles profile on profile.id = submission.student_id
                left join public.assignment_student_extensions student_extension
                  on student_extension.assignment_id = submission.assignment_id
                 and student_extension.student_id = submission.student_id
                left join public.student_activity_attempts attempt on attempt.submission_id = submission.id
                left join lateral (
                  select teacher_review.id, teacher_review.feedback, teacher_review.reviewed_at,
                    coalesce(teacher_review.reflection_due_at, assignment.reflection_due_at) reflection_due_at
                  from public.teacher_activity_reviews teacher_review
                  where teacher_review.attempt_id = attempt.id
                  order by teacher_review.reviewed_at desc limit 1
                ) review on true
                 left join lateral (
                   select submission_file.id
                   from public.submission_files submission_file
                   where submission_file.submission_id = submission.id
                     and lower(submission_file.mime_type) like 'audio/%'
                   order by submission_file.uploaded_at desc
                   limit 1
                 ) audio on true
                where submission.id = ? and submission.student_id = ?
                """, (rs, row) -> submissionResponse(courseId,
                rs.getObject("id", UUID.class), rs.getObject("assignment_id", UUID.class),
                rs.getObject("class_activity_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getString("full_name"), rs.getShort("attempt_no"), rs.getString("text_content"),
                upper(rs.getString("status")), rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getBoolean("is_late"), rs.getBigDecimal("score"), upper(rs.getString("review_status")),
                rs.getString("reflection"), rs.getObject("feedback_id", UUID.class),
                 rs.getString("feedback"), rs.getObject("reviewed_at", OffsetDateTime.class),
                 rs.getObject("extension_due_at", OffsetDateTime.class),
                 rs.getString("extension_reason"),
                 rs.getObject("reflection_due_at", OffsetDateTime.class),
                 rs.getObject("audio_id", UUID.class) == null ? null
                         : studentAudioUrl(courseId, rs.getObject("class_activity_id", UUID.class), rs.getObject("id", UUID.class))
        ), submissionId, studentId);
        if (values.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy submission");
        return values.getFirst();
    }

    private AttemptContext latestAttempt(UUID classActivityId, UUID studentId) {
        var values = jdbc.query("""
                select attempt.id, attempt.submission_id, attempt.attempt_no,
                  attempt.status::text status, attempt.review_status::text review_status,
                  attempt.reflection, submission.extension_due_at
                from public.student_activity_attempts attempt
                join public.submissions submission on submission.id = attempt.submission_id
                where class_activity_id = ? and student_id = ?
                order by attempt_no desc limit 1
                """, (rs, row) -> new AttemptContext(
                rs.getObject("id", UUID.class), rs.getObject("submission_id", UUID.class),
                rs.getShort("attempt_no"), upper(rs.getString("status")), upper(rs.getString("review_status")),
                rs.getString("reflection"), rs.getObject("extension_due_at", OffsetDateTime.class)
        ), classActivityId, studentId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private StudentSubmissionResponse submissionResponse(UUID courseId, UUID id, UUID assignmentId, UUID classActivityId,
                                                          UUID studentId, String studentName, short attemptNo,
                                                          String textContent, String status, OffsetDateTime submittedAt,
                                                          boolean late, BigDecimal score, String reviewStatus,
                                                          String reflection, UUID feedbackId, String feedback,
                                                          OffsetDateTime feedbackAt, OffsetDateTime extensionDueAt,
                                                          String extensionReason, OffsetDateTime reflectionDueAt,
                                                          String audioUrl) {
        boolean admin = audioUrl != null && audioUrl.startsWith("/admin/");
        return new StudentSubmissionResponse(id, assignmentId, classActivityId, studentId, studentName, attemptNo,
                textContent, status, submittedAt, late, score, reviewStatus, reflection, feedbackId, feedback, feedbackAt,
                audioUrl, audioFiles(id, courseId, classActivityId, admin), criteria(feedbackId),
                annotations(feedbackId), extensionDueAt, extensionReason, reflectionDueAt);
    }

    private List<SubmissionAudioResponse> audioFiles(UUID submissionId, UUID courseId, UUID classActivityId,
                                                     boolean admin) {
        return jdbc.query("""
                select id, prompt_key, prompt_title, sort_order, original_name, mime_type, size_bytes
                from public.submission_files
                where submission_id = ? and lower(coalesce(mime_type, '')) like 'audio/%'
                order by sort_order, uploaded_at, id
                """, (rs, row) -> {
            UUID fileId = rs.getObject("id", UUID.class);
            return new SubmissionAudioResponse(
                    fileId,
                    rs.getString("prompt_key"),
                    rs.getString("prompt_title"),
                    rs.getInt("sort_order"),
                    rs.getString("original_name"),
                    rs.getString("mime_type"),
                    rs.getLong("size_bytes"),
                    admin
                            ? adminAudioUrl(courseId, classActivityId, submissionId, fileId)
                            : studentAudioUrl(courseId, classActivityId, submissionId, fileId));
        }, submissionId);
    }

    private List<ReviewCriterionResponse> criteria(UUID reviewId) {
        if (reviewId == null) return List.of();
        return jdbc.query("""
                select criterion, score, feedback, sort_order
                from public.teacher_review_criteria
                where review_id = ?
                order by sort_order, criterion
                """, (rs, row) -> new ReviewCriterionResponse(
                rs.getString("criterion"), rs.getBigDecimal("score"), rs.getString("feedback"),
                rs.getInt("sort_order")), reviewId);
    }

    private List<InlineAnnotationResponse> annotations(UUID reviewId) {
        if (reviewId == null) return List.of();
        return jdbc.query("""
                select id, start_offset, end_offset, quoted_text, comment, suggestion, sort_order
                from public.teacher_review_annotations
                where review_id = ?
                order by sort_order, start_offset
                """, (rs, row) -> new InlineAnnotationResponse(
                rs.getObject("id", UUID.class), rs.getInt("start_offset"), rs.getInt("end_offset"),
                rs.getString("quoted_text"), rs.getString("comment"), rs.getString("suggestion"),
                rs.getInt("sort_order")), reviewId);
    }

    private List<ReviewCriterionRequest> safeCriteria(ReviewSubmissionRequest request) {
        return request.criteria() == null ? List.of() : request.criteria();
    }

    private List<InlineAnnotationRequest> safeAnnotations(ReviewSubmissionRequest request) {
        return request.annotations() == null ? List.of() : request.annotations();
    }

    private void validateStructuredReview(ReviewSource source, ReviewSubmissionRequest request) {
        Set<String> allowedCriteria = "SPEAKING".equals(source.skill())
                ? Set.of("FLUENCY_COHERENCE", "LEXICAL_RESOURCE", "GRAMMATICAL_RANGE_ACCURACY", "PRONUNCIATION")
                : Set.of("TASK_ACHIEVEMENT", "TASK_RESPONSE", "COHERENCE_COHESION", "LEXICAL_RESOURCE",
                "GRAMMATICAL_RANGE_ACCURACY");
        var seen = new HashSet<String>();
        for (var criterion : safeCriteria(request)) {
            String key = upper(criterion.criterion());
            if (!allowedCriteria.contains(key)) {
                throw new BusinessRuleException("Tiêu chí rubric không phù hợp với kỹ năng " + source.skill());
            }
            if (!seen.add(key)) throw new BusinessRuleException("Mỗi tiêu chí rubric chỉ được chấm một lần");
        }

        var reviewAnnotations = safeAnnotations(request).stream()
                .sorted((left, right) -> Integer.compare(left.startOffset(), right.startOffset()))
                .toList();
        if (!reviewAnnotations.isEmpty() && !"WRITING".equals(source.skill())) {
            throw new BusinessRuleException("Inline comment chỉ áp dụng cho bài Writing");
        }
        String text = source.textContent() == null ? "" : source.textContent();
        int previousEnd = -1;
        for (var annotation : reviewAnnotations) {
            if (annotation.endOffset() > text.length() || annotation.startOffset() >= annotation.endOffset()) {
                throw new BusinessRuleException("Vị trí inline comment không hợp lệ");
            }
            if (!text.substring(annotation.startOffset(), annotation.endOffset()).equals(annotation.quotedText())) {
                throw new BusinessRuleException("Đoạn trích inline comment không khớp bài nộp hiện tại");
            }
            if (annotation.startOffset() < previousEnd) {
                throw new BusinessRuleException("Các inline comment không được chồng lấn");
            }
            previousEnd = annotation.endOffset();
        }
    }

    private void requireEnrollment(UUID studentId, UUID courseId) {
        Boolean enrolled = jdbc.queryForObject("""
                select exists (select 1 from public.enrollments
                  where student_id = ? and course_id = ? and status <> 'WITHDRAWN'::public.enrollment_status)
                """, Boolean.class, studentId, courseId);
        if (!Boolean.TRUE.equals(enrolled)) throw new ResourceNotFoundException("Bạn chưa ghi danh khóa học này");
    }

    private void requireActiveEnrollment(UUID studentId, UUID courseId) {
        Boolean enrolled = jdbc.queryForObject("""
                select exists (select 1 from public.enrollments
                  where student_id = ? and course_id = ? and status = 'ACTIVE'::public.enrollment_status)
                """, Boolean.class, studentId, courseId);
        if (!Boolean.TRUE.equals(enrolled)) throw new ResourceNotFoundException("Học viên không còn trong lớp học này");
    }

    private OffsetDateTime assignmentStudentExtension(UUID assignmentId, UUID studentId) {
        var values = jdbc.query("""
                select extension_due_at
                from public.assignment_student_extensions
                where assignment_id = ? and student_id = ?
                """, (rs, row) -> rs.getObject("extension_due_at", OffsetDateTime.class), assignmentId, studentId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private void requireEnrollmentIfStudent(UUID studentId, UUID courseId) {
        if (studentId != null) requireEnrollment(studentId, courseId);
    }

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException("Vui lòng chọn file audio Speaking");
        if (file.getSize() > MAX_AUDIO_BYTES) throw new BusinessRuleException("Audio không được vượt quá 50 MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!AUDIO_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Chỉ hỗ trợ audio WebM, OGG, MP3, M4A hoặc WAV");
        }
    }

    private String audioExtension(String contentType) {
        return switch (contentType) {
            case "audio/webm" -> ".webm";
            case "audio/ogg" -> ".ogg";
            case "audio/mpeg" -> ".mp3";
            case "audio/mp4" -> ".m4a";
            case "audio/wav", "audio/x-wav" -> ".wav";
            default -> throw new BusinessRuleException("Định dạng audio không hợp lệ");
        };
    }

    private String normalizedPromptKey(String value) {
        String key = blank(value);
        if (key == null) return "response-1";
        key = key.toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new BusinessRuleException("Mã phần Speaking chỉ gồm chữ thường, số, gạch ngang hoặc gạch dưới");
        }
        return key;
    }

    private void validateSchedule(OffsetDateTime opensAt, OffsetDateTime dueAt) {
        if (opensAt != null && dueAt != null && !dueAt.isAfter(opensAt)) {
            throw new BusinessRuleException("Deadline phải sau thời điểm mở assignment");
        }
    }

    private void validateReflectionDeadline(OffsetDateTime reflectionDueAt, OffsetDateTime dueAt) {
        if (reflectionDueAt != null && dueAt != null && reflectionDueAt.isBefore(dueAt)) {
            throw new BusinessRuleException("Deadline reflection không được trước deadline nộp bài");
        }
    }

    private String normalizedLatePolicy(String value) {
        String policy = blank(value);
        if (policy == null) return "MARK_LATE";
        policy = policy.toUpperCase(Locale.ROOT);
        if (!Set.of("ALLOW", "MARK_LATE", "BLOCK").contains(policy)) {
            throw new BusinessRuleException("Late policy không hợp lệ");
        }
        return policy;
    }

    private int normalizedGraceMinutes(Integer value) {
        int minutes = value == null ? 0 : value;
        if (minutes < 0 || minutes > 10080) {
            throw new BusinessRuleException("Khoảng grace late phải từ 0 đến 10080 phút");
        }
        return minutes;
    }

    private String normalizedContactChannel(String value) {
        String channel = blank(value);
        if (channel == null) return "IN_APP";
        channel = channel.toUpperCase(Locale.ROOT);
        if (!Set.of("IN_APP", "MANUAL", "EMAIL").contains(channel)) {
            throw new BusinessRuleException("Kênh liên lạc không hợp lệ");
        }
        return channel;
    }

    private OffsetDateTime later(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private String normalizedType(String requested, String skill) {
        String value = blank(requested);
        if (value == null) value = skill == null ? "general" : skill.toLowerCase(Locale.ROOT);
        value = value.toLowerCase(Locale.ROOT);
        if (!List.of("general", "listening", "reading", "writing", "speaking", "vocabulary").contains(value)) {
            throw new BusinessRuleException("Loại assignment không hợp lệ");
        }
        return value;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String adminAudioUrl(UUID courseId, UUID classActivityId, UUID submissionId) {
        return "/admin/courses/" + courseId + "/activities/" + classActivityId + "/submissions/"
                + submissionId + "/audio";
    }

    private String adminAudioUrl(UUID courseId, UUID classActivityId, UUID submissionId, UUID fileId) {
        return adminAudioUrl(courseId, classActivityId, submissionId) + "/" + fileId;
    }

    private String studentAudioUrl(UUID courseId, UUID classActivityId, UUID submissionId) {
        return "/student/portal/courses/" + courseId + "/activities/" + classActivityId
                + "/submissions/" + submissionId + "/audio";
    }

    private String studentAudioUrl(UUID courseId, UUID classActivityId, UUID submissionId, UUID fileId) {
        return studentAudioUrl(courseId, classActivityId, submissionId) + "/" + fileId;
    }

    private record ActivityContext(UUID id, UUID courseId, UUID sessionId, UUID activityId, UUID assignmentId,
                                   String title, String skill, boolean requiresTeacherReview) {}

    private record AssignmentContext(UUID id, UUID courseId, boolean allowResubmission, OffsetDateTime opensAt,
                                     OffsetDateTime dueAt, BigDecimal maxScore, String status,
                                     String latePolicy, int lateGraceMinutes, OffsetDateTime reflectionDueAt) {}

    private record AttemptContext(UUID attemptId, UUID submissionId, short attemptNo, String status,
                                  String reviewStatus, String reflection, OffsetDateTime extensionDueAt) {}

    public record SubmissionAudio(InputStream inputStream, String mimeType, Long sizeBytes, String originalName) {}

    private record SubmissionContext(ActivityContext activity, AssignmentContext assignment,
                                     UUID submissionId, UUID attemptId, short attemptNo,
                                     OffsetDateTime extensionDueAt) {}

    private record ReviewSource(UUID submissionId, UUID assignmentId, UUID studentId, short attemptNo,
                                UUID classActivityId, UUID attemptId, BigDecimal maxScore, String skill,
                                String textContent) {}

    private record ExtensionSource(UUID studentId, UUID assignmentId) {}
}
