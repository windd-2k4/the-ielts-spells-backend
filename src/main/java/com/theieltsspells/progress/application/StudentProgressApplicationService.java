package com.theieltsspells.progress.application;

import com.theieltsspells.progress.application.dto.StudentProgressTaskResponse;
import com.theieltsspells.progress.application.dto.UpdateProgressSummaryRequest;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProgressApplicationService {
    private static final long MAX_EVIDENCE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of(
            MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp");

    private final JdbcTemplate jdbc;
    private final FileStorage storage;

    @Transactional(readOnly = true)
    public List<StudentProgressTaskResponse> list(UUID studentId, String courseKey) {
        UUID courseId = courseId(courseKey);
        requireEnrollment(studentId, courseId);
        var tasks = jdbc.query("""
                select ca.id class_activity_id, cs.id session_id, cs.session_no,
                  coalesce(cs.title, 'Session ' || cs.session_no) session_title,
                  la.id activity_id, la.title, la.skill::text skill,
                  la.completion_method::text completion_method,
                  attempt.id attempt_id, attempt.source::text source, attempt.status::text status,
                  attempt.score, attempt.max_score, attempt.duration_seconds,
                  attempt.comprehension_percent, attempt.error_analysis,
                  (select count(*) from public.activity_evidence ae where ae.attempt_id = attempt.id)::int evidence_count
                from public.course_activities ca
                join public.learning_activities la on la.id = ca.activity_id
                join public.course_sessions cs on cs.id = ca.session_id
                left join lateral (
                  select saa.* from public.student_activity_attempts saa
                  where saa.class_activity_id = ca.id and saa.student_id = ?
                  order by saa.attempt_no desc limit 1
                ) attempt on true
                where ca.course_id = ? and ca.is_published = true
                order by cs.session_no, la.display_order
                """, (rs, row) -> new StudentProgressTaskResponse(
                rs.getObject("class_activity_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getShort("session_no"),
                rs.getString("session_title"),
                rs.getObject("activity_id", UUID.class),
                rs.getString("title"),
                rs.getString("skill").toUpperCase(Locale.ROOT),
                rs.getString("completion_method").toUpperCase(Locale.ROOT),
                rs.getObject("attempt_id", UUID.class),
                upper(rs.getString("source")),
                upper(rs.getString("status")),
                rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"),
                rs.getObject("duration_seconds", Integer.class),
                rs.getObject("comprehension_percent", Short.class),
                rs.getString("error_analysis"),
                rs.getInt("evidence_count"),
                List.of()
        ), studentId, courseId);

        return tasks.stream().map(task -> new StudentProgressTaskResponse(
                task.classActivityId(), task.sessionId(), task.sessionNo(), task.sessionTitle(),
                task.activityId(), task.title(), task.skill(), task.completionMethod(),
                task.attemptId(), task.source(), task.status(), task.score(), task.maxScore(),
                task.durationSeconds(), task.comprehensionPercent(), task.errorAnalysis(), task.evidenceCount(),
                checklist(task.activityId(), task.attemptId())
        )).toList();
    }

    @Transactional
    public UUID uploadEvidence(UUID studentId, String courseKey, UUID classActivityId, MultipartFile file) {
        validateImage(file);
        UUID courseId = courseId(courseKey);
        requireEnrollment(studentId, courseId);
        requireClassActivity(courseId, classActivityId);
        UUID attemptId = latestAttempt(classActivityId, studentId);
        if (attemptId == null) {
            attemptId = UUID.randomUUID();
            jdbc.update("""
                    insert into public.student_activity_attempts
                      (id, class_activity_id, student_id, attempt_no, source, status, review_status,
                       submitted_at, completed_at, is_late, metadata, created_at, updated_at)
                    values (?, ?, ?, 1, 'STUDENT_MANUAL'::public.result_source,
                      'COMPLETED'::public.activity_attempt_status, 'PENDING'::public.review_status,
                      now(), now(), false, '{}'::jsonb, now(), now())
                    """, attemptId, classActivityId, studentId);
        } else {
            jdbc.update("""
                    update public.student_activity_attempts
                    set status = 'COMPLETED'::public.activity_attempt_status,
                        submitted_at = coalesce(submitted_at, now()),
                        completed_at = coalesce(completed_at, now()),
                        review_status = case when review_status = 'NOT_REQUIRED'::public.review_status
                          then 'PENDING'::public.review_status else review_status end,
                        updated_at = now()
                    where id = ?
                    """, attemptId);
        }

        String extension = extension(file.getContentType());
        String objectPath = "progress/evidence/" + studentId + "/" + UUID.randomUUID() + extension;
        storage.store(objectPath, file);
        jdbc.update("""
                insert into public.activity_evidence
                  (attempt_id, evidence_type, storage_path, original_name, mime_type, size_bytes, uploaded_at)
                values (?, 'image', ?, ?, ?, ?, ?)
                """, attemptId, objectPath, file.getOriginalFilename(), file.getContentType(),
                file.getSize(), OffsetDateTime.now());
        return attemptId;
    }

    @Transactional
    public void updateChecklist(UUID studentId, String courseKey, UUID attemptId, UUID checklistItemId,
            boolean completed) {
        UUID courseId = courseId(courseKey);
        requireEnrollment(studentId, courseId);
        Boolean unlocked = jdbc.queryForObject("""
                select exists (
                  select 1 from public.student_activity_attempts saa
                  join public.course_activities ca on ca.id = saa.class_activity_id
                  join public.activity_checklist_items item on item.activity_id = ca.activity_id
                  where saa.id = ? and saa.student_id = ? and ca.course_id = ? and item.id = ?
                    and (saa.completed_at is not null or saa.submitted_at is not null
                      or exists (select 1 from public.activity_evidence ae where ae.attempt_id = saa.id))
                )
                """, Boolean.class, attemptId, studentId, courseId, checklistItemId);
        if (!Boolean.TRUE.equals(unlocked)) {
            throw new BusinessRuleException("Hãy hoàn thành bài được giao hoặc gửi ảnh minh chứng trước.");
        }
        jdbc.update("""
                insert into public.attempt_checklist_results
                  (attempt_id, checklist_item_id, is_completed, completed_at)
                values (?, ?, ?, case when ? then now() else null end)
                on conflict (attempt_id, checklist_item_id) do update
                set is_completed = excluded.is_completed, completed_at = excluded.completed_at
                """, attemptId, checklistItemId, completed, completed);
    }

    @Transactional
    public void updateSummary(UUID studentId, String courseKey, UUID attemptId, UpdateProgressSummaryRequest request) {
        UUID courseId = courseId(courseKey);
        requireEnrollment(studentId, courseId);
        int updated = jdbc.update("""
                update public.student_activity_attempts saa
                set duration_seconds = coalesce(?, duration_seconds),
                    comprehension_percent = coalesce(?, comprehension_percent),
                    updated_at = now()
                from public.course_activities ca
                where saa.id = ? and saa.student_id = ? and ca.id = saa.class_activity_id and ca.course_id = ?
                """, request.durationSeconds(), request.comprehensionPercent(), attemptId, studentId, courseId);
        if (updated == 0) throw new ResourceNotFoundException("Không tìm thấy tiến độ bài học.");
    }

    private List<StudentProgressTaskResponse.ChecklistItem> checklist(UUID activityId, UUID attemptId) {
        return jdbc.query("""
                select item.id, item.label, item.description, item.is_required,
                  coalesce(result.is_completed, false) completed
                from public.activity_checklist_items item
                left join public.attempt_checklist_results result
                  on result.checklist_item_id = item.id and result.attempt_id = ?
                where item.activity_id = ?
                order by item.display_order
                """, (rs, row) -> new StudentProgressTaskResponse.ChecklistItem(
                rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("description"),
                rs.getBoolean("is_required"), rs.getBoolean("completed")
        ), attemptId, activityId);
    }

    private UUID latestAttempt(UUID classActivityId, UUID studentId) {
        var ids = jdbc.query("""
                select id from public.student_activity_attempts
                where class_activity_id = ? and student_id = ?
                order by attempt_no desc limit 1
                """, (rs, row) -> rs.getObject("id", UUID.class), classActivityId, studentId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void requireClassActivity(UUID courseId, UUID classActivityId) {
        Boolean exists = jdbc.queryForObject(
                "select exists (select 1 from public.course_activities where id = ? and course_id = ? and is_published = true)",
                Boolean.class, classActivityId, courseId);
        if (!Boolean.TRUE.equals(exists)) throw new ResourceNotFoundException("Không tìm thấy bài học.");
    }

    private void requireEnrollment(UUID studentId, UUID courseId) {
        Boolean enrolled = jdbc.queryForObject("""
                select exists (select 1 from public.enrollments
                  where student_id = ? and course_id = ? and status <> 'WITHDRAWN')
                """, Boolean.class, studentId, courseId);
        if (!Boolean.TRUE.equals(enrolled)) throw new ResourceNotFoundException("Bạn chưa ghi danh khóa học này.");
    }

    private UUID courseId(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException ignored) {
            var ids = jdbc.query("select id from public.courses where lower(code) = lower(?) limit 1",
                    (rs, row) -> rs.getObject("id", UUID.class), key);
            if (ids.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy khóa học.");
            return ids.get(0);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException("Vui lòng chọn ảnh minh chứng.");
        if (file.getSize() > MAX_EVIDENCE_BYTES) throw new BusinessRuleException("Ảnh minh chứng không được vượt quá 10 MB.");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(contentType)) throw new BusinessRuleException("Chỉ hỗ trợ ảnh PNG, JPG hoặc WebP.");
    }

    private String extension(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case MediaType.IMAGE_JPEG_VALUE -> ".jpg";
            case "image/webp" -> ".webp";
            default -> throw new BusinessRuleException("Định dạng ảnh không hợp lệ.");
        };
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
