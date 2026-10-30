package com.theieltsspells.progress.application;

import com.theieltsspells.curriculum.application.ClassActivityQueryService;
import com.theieltsspells.curriculum.domain.ClassActivity;
import com.theieltsspells.curriculum.domain.ActivityChecklistItem;
import com.theieltsspells.curriculum.infrastructure.persistence.ActivityChecklistItemRepository;
import com.theieltsspells.progress.application.dto.ActivityAttemptResponse;
import com.theieltsspells.progress.application.dto.ActivityChecklistItemResponse;
import com.theieltsspells.progress.application.dto.AttemptChecklistResultResponse;
import com.theieltsspells.progress.application.dto.ActivityEvidenceResponse;
import com.theieltsspells.progress.application.dto.ClassActivityProgressResponse;
import com.theieltsspells.progress.application.dto.CreateCourseActivityRequest;
import com.theieltsspells.progress.domain.ActivityEvidence;
import com.theieltsspells.progress.domain.AttemptChecklistResult;
import com.theieltsspells.progress.domain.StudentActivityAttempt;
import com.theieltsspells.progress.infrastructure.persistence.ActivityEvidenceRepository;
import com.theieltsspells.progress.infrastructure.persistence.AttemptChecklistResultRepository;
import com.theieltsspells.progress.infrastructure.persistence.StudentActivityAttemptRepository;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassProgressQueryService {
    private final ClassActivityQueryService classActivities;
    private final StudentActivityAttemptRepository attempts;
    private final ActivityChecklistItemRepository checklistItems;
    private final AttemptChecklistResultRepository checklistResults;
    private final ActivityEvidenceRepository evidence;
    private final JdbcTemplate jdbc;

    @Transactional
    public UUID createActivity(UUID courseId, CreateCourseActivityRequest request, UUID actorId) {
        var sessions = jdbc.query("""
                select starts_at
                from public.course_sessions
                where id = ? and course_id = ?
                """, (rs, rowNum) -> new SessionWindow(
                rs.getObject("starts_at", OffsetDateTime.class)), request.sessionId(), courseId);
        if (sessions.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy session trong khóa học này");
        }
        var session = sessions.getFirst();
        if (request.dueAt() != null && !request.dueAt().isAfter(session.startsAt())) {
            throw new BusinessRuleException("Deadline phải sau thời gian bắt đầu session");
        }

        var courses = jdbc.query("""
                select program_id
                from public.courses
                where id = ?
                """, (rs, rowNum) -> new CourseContext(
                rs.getObject("program_id", UUID.class)), courseId);
        if (courses.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy khóa học");
        }
        var course = courses.getFirst();
        UUID programId = course.programId();
        if (programId == null) {
            programId = jdbc.queryForObject("""
                    insert into public.course_programs(
                      code, name, description, total_sessions, is_public, is_active, created_by
                    )
                    select 'AUTO-' || code, name || ' - Lộ trình', description,
                           total_sessions, false, true, ?
                    from public.courses
                    where id = ?
                    on conflict (code) do update set updated_at = now()
                    returning id
                    """, UUID.class, actorId, courseId);
            jdbc.update("update public.courses set program_id = ?, updated_at = now() where id = ? and program_id is null",
                    programId, courseId);
        }

        var moduleIds = jdbc.queryForList("""
                select id
                from public.course_modules
                where program_id = ? and skill = cast(? as public.skill_type)
                order by display_order, created_at
                limit 1
                """, UUID.class, programId, request.skill().name());
        UUID moduleId = moduleIds.isEmpty() ? jdbc.queryForObject("""
                insert into public.course_modules(program_id, title, skill, display_order, is_required)
                select ?, initcap(lower(?)), cast(? as public.skill_type),
                       (coalesce(max(display_order), -1) + 1)::smallint, true
                from public.course_modules
                where program_id = ?
                returning id
                """, UUID.class, programId, request.skill().name(), request.skill().name(), programId) : moduleIds.getFirst();

        String activityType = switch (request.skill()) {
            case WRITING -> "WRITING_SUBMISSION";
            case SPEAKING -> "AUDIO_SUBMISSION";
            default -> "FILE_SUBMISSION";
        };
        UUID activityId = jdbc.queryForObject("""
                insert into public.learning_activities(
                  module_id, title, description, skill, activity_type, completion_method,
                  display_order, is_required, requires_teacher_review,
                  requires_error_analysis, requires_comprehension_rating,
                  allowed_evidence_types, created_by
                )
                select ?, ?, 'Hoạt động được tạo trực tiếp trong Course Workspace.',
                       cast(? as public.skill_type), cast(? as public.activity_type),
                       'WEB_OR_MANUAL'::public.completion_method,
                       (coalesce(max(display_order), -1) + 1)::smallint, true, true, true, true,
                       array['image/png', 'image/jpeg', 'image/webp', 'application/pdf'], ?
                from public.learning_activities
                where module_id = ?
                returning id
                """, UUID.class, moduleId, request.title().trim(), request.skill().name(),
                activityType, actorId, moduleId);

        addDefaultChecklist(activityId, request.skill());

        return jdbc.queryForObject("""
                insert into public.course_activities(
                  course_id, session_id, activity_id, opens_at, due_at,
                  is_required_override, is_published, created_by
                ) values (?, ?, ?, ?, ?, true, true, ?)
                returning id
                """, UUID.class, courseId, request.sessionId(), activityId,
                session.startsAt(), request.dueAt(), actorId);
    }

    private void addDefaultChecklist(UUID activityId, SkillType skill) {
        String[][] items = switch (skill) {
            case WRITING -> new String[][] {
                    {"Hoàn thành bài viết", "Nộp bài viết hoặc minh chứng bài làm."},
                    {"Tự kiểm tra lỗi", "Rà soát ngữ pháp, từ vựng và cấu trúc bài."},
                    {"Ghi chú điểm cần cải thiện", "Tóm tắt lỗi và hành động cần luyện thêm."}
            };
            case SPEAKING -> new String[][] {
                    {"Hoàn thành bài nói", "Nộp audio hoặc minh chứng bài làm."},
                    {"Nghe lại bài nói", "Tự kiểm tra phát âm, độ trôi chảy và nội dung."},
                    {"Ghi chú điểm cần cải thiện", "Tóm tắt lỗi và hành động cần luyện thêm."}
            };
            default -> new String[][] {
                    {"Dịch bài", "Dịch lại nội dung để kiểm tra độ hiểu chính xác."},
                    {"Từ vựng + test", "Lưu từ mới và hoàn thành bài kiểm tra từ vựng."},
                    {"Đọc hiểu không cần từ vựng", "Đọc lại và tự kiểm tra khả năng hiểu khi không tra từ."}
            };
        };
        for (int index = 0; index < items.length; index++) {
            jdbc.update("""
                    insert into public.activity_checklist_items(
                      activity_id, label, description, display_order, is_required
                    ) values (?, ?, ?, ?, true)
                    """, activityId, items[index][0], items[index][1], index + 1);
        }
    }

    public List<ClassActivityProgressResponse> courseProgress(UUID courseId) {
        return courseProgress(courseId, null, null);
    }

    public List<ClassActivityProgressResponse> courseProgress(UUID courseId, UUID sessionId, SkillType skill) {
        var activities = sessionId == null || skill == null
                ? classActivities.publishedForCourse(courseId)
                : classActivities.publishedForCourse(courseId, sessionId, skill);
        if (activities.isEmpty()) {
            return List.of();
        }

        var activityIds = activities.stream().map(ClassActivity::getId).toList();
        Map<UUID, List<StudentActivityAttempt>> attemptsByActivity = attempts
                .findByClassActivityIdInOrderByAttemptNoDesc(activityIds)
                .stream()
                .collect(Collectors.groupingBy(StudentActivityAttempt::getClassActivityId));
        attemptsByActivity.replaceAll((activityId, values) -> values.stream()
                .collect(Collectors.groupingBy(StudentActivityAttempt::getStudentId,
                        Collectors.collectingAndThen(Collectors.maxBy(
                                Comparator.comparingInt(StudentActivityAttempt::getAttemptNo)),
                                latest -> latest.orElseThrow())))
                .values().stream().toList());
        var learningActivityIds = activities.stream().map(ClassActivity::getActivityId).toList();
        Map<UUID, List<ActivityChecklistItem>> checklistByActivity = checklistItems
                .findByActivityIdInOrderByDisplayOrder(learningActivityIds).stream()
                .collect(Collectors.groupingBy(ActivityChecklistItem::getActivityId));
        var allAttempts = attemptsByActivity.values().stream().flatMap(List::stream).toList();
        var attemptIds = allAttempts.stream().map(StudentActivityAttempt::getId).toList();
        Map<UUID, List<AttemptChecklistResult>> checklistByAttempt = attemptIds.isEmpty() ? Map.of()
                : checklistResults.findByAttemptIdIn(attemptIds).stream()
                .collect(Collectors.groupingBy(AttemptChecklistResult::getAttemptId));
        Map<UUID, List<ActivityEvidence>> evidenceByAttempt = attemptIds.isEmpty() ? Map.of()
                : evidence.findByAttemptIdIn(attemptIds).stream()
                .collect(Collectors.groupingBy(ActivityEvidence::getAttemptId));

        return activities.stream()
                .map(activity -> response(
                        activity,
                        checklistByActivity.getOrDefault(activity.getActivityId(), List.of()),
                        attemptsByActivity.getOrDefault(activity.getId(), List.of()),
                        checklistByAttempt,
                        evidenceByAttempt))
                .toList();
    }

    private ClassActivityProgressResponse response(
            ClassActivity classActivity,
            List<ActivityChecklistItem> activityChecklist,
            List<StudentActivityAttempt> activityAttempts,
            Map<UUID, List<AttemptChecklistResult>> checklistByAttempt,
            Map<UUID, List<ActivityEvidence>> evidenceByAttempt
    ) {
        var activity = classActivity.getActivityRef();
        boolean required = classActivity.getIsRequiredOverride() != null
                ? classActivity.getIsRequiredOverride()
                : Boolean.TRUE.equals(activity.getIsRequired());
        return new ClassActivityProgressResponse(
                classActivity.getId(),
                classActivity.getSessionId(),
                classActivity.getActivityId(),
                activity.getTitle(),
                activity.getSkill(),
                activity.getActivityType(),
                activity.getCompletionMethod(),
                classActivity.getOpensAt(),
                classActivity.getDueAt(),
                required,
                classActivity.getAssignmentId(),
                classActivity.getTestAssignmentId(),
                activityChecklist.stream().map(item -> new ActivityChecklistItemResponse(
                        item.getId(), item.getLabel(), item.getDescription(),
                        item.getDisplayOrder(), Boolean.TRUE.equals(item.getIsRequired()))).toList(),
                activityAttempts.stream().map(value -> attempt(
                        value,
                        checklistByAttempt.getOrDefault(value.getId(), List.of()),
                        evidenceByAttempt.getOrDefault(value.getId(), List.of()))).toList());
    }

    private ActivityAttemptResponse attempt(StudentActivityAttempt attempt,
            List<AttemptChecklistResult> results, List<ActivityEvidence> attemptEvidence) {
        return new ActivityAttemptResponse(
                attempt.getId(),
                attempt.getStudentId(),
                attempt.getAttemptNo(),
                attempt.getSource(),
                attempt.getStatus(),
                attempt.getReviewStatus(),
                attempt.getScore(),
                attempt.getMaxScore(),
                attempt.getCorrectCount(),
                attempt.getIncorrectCount(),
                attempt.getUnansweredCount(),
                attempt.getDurationSeconds(),
                attempt.getComprehensionPercent(),
                attempt.getErrorAnalysis(),
                attempt.getImprovementPlan(),
                attempt.getSubmittedAt(),
                attempt.getCompletedAt(),
                Boolean.TRUE.equals(attempt.getIsLate()),
                results.stream().map(result -> new AttemptChecklistResultResponse(
                        result.getChecklistItemId(), Boolean.TRUE.equals(result.getIsCompleted()),
                        result.getCompletedAt(), result.getNote())).toList(),
                attemptEvidence.stream().map(item -> new ActivityEvidenceResponse(
                        item.getId(), item.getEvidenceType(), item.getOriginalName(), item.getUploadedAt())).toList());
    }

    private record SessionWindow(OffsetDateTime startsAt) {}
    private record CourseContext(UUID programId) {}
}
