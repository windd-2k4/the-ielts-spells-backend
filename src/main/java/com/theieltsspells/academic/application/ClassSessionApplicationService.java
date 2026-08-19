package com.theieltsspells.academic.application;

import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.academic.domain.ClassSession;
import com.theieltsspells.academic.domain.CourseSessionItem;
import com.theieltsspells.academic.infrastructure.persistence.ClassSessionRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseRepository;
import com.theieltsspells.academic.infrastructure.persistence.CourseSessionItemRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassSessionApplicationService {
    private final ClassSessionRepository sessions;
    private final CourseSessionItemRepository sessionItems;
    private final CourseRepository courses;

    public List<ClassSessionResponse> list(UUID courseId) {
        requireCourse(courseId);
        return sessions.findByCourseIdOrderBySessionNo(courseId).stream().map(this::response).toList();
    }

    @Transactional
    public ClassSessionResponse create(UUID courseId, UpsertClassSessionRequest request) {
        requireCourse(courseId);
        validate(request);
        if (sessions.existsByCourseIdAndSessionNo(courseId, request.sessionNo())) {
            throw new ConflictException("Số thứ tự session đã tồn tại trong khóa học");
        }
        var value = new ClassSession();
        value.setCourseId(courseId);
        apply(value, request);
        value = sessions.save(value);
        replaceItems(value, request);
        return response(value);
    }

    @Transactional
    public List<ClassSessionResponse> bulkCreate(UUID courseId, BulkCreateSessionsRequest request) {
        requireCourse(courseId);
        var orderedEntries = request.entries().stream()
                .sorted(Comparator.comparingInt(ScheduleTemplateEntryRequest::sessionNo)).toList();
        orderedEntries.forEach(entry -> {
            if (sessions.existsByCourseIdAndSessionNo(courseId, entry.sessionNo())) {
                throw new ConflictException("Session " + entry.sessionNo() + " đã tồn tại trong khóa học");
            }
        });
        var slots = request.weeklySlots().stream()
                .sorted(Comparator.comparingInt(WeeklySlotRequest::dayOfWeek)).toList();
        var cursor = request.startsOn().atStartOfDay().minusNanos(1).atOffset(ZoneOffset.ofHours(7));
        var results = new ArrayList<ClassSessionResponse>();
        for (var entry : orderedEntries) {
            var occurrence = nextOccurrence(cursor, slots);
            var value = new ClassSession();
            value.setCourseId(courseId);
            value.setSessionNo(entry.sessionNo());
            value.setTitle("Session " + entry.sessionNo());
            value.setPhaseName(blank(entry.phaseName()));
            value.setContent(String.join("\n", entry.contents()));
            value.setStartsAt(occurrence.startsAt());
            value.setEndsAt(occurrence.endsAt());
            value.setTeacherId(request.teacherId());
            value.setZoomUrl(blank(request.zoomUrl()));
            value.setStatus(SessionStatus.SCHEDULED);
            value = sessions.save(value);
            if ("TEST".equalsIgnoreCase(entry.entryType())) {
                var test = new CourseSessionItem();
                test.setSessionId(value.getId());
                test.setItemType("TEST");
                test.setTitle(entry.contents().getFirst());
                test.setDeadlineAt(value.getEndsAt().plusDays(2));
                test.setIsRequired(true);
                test.setVisibility("STUDENT");
                test.setDisplayOrder((short) 0);
                sessionItems.save(test);
            }
            results.add(response(value));
            cursor = occurrence.endsAt();
        }
        return results;
    }

    @Transactional
    public List<ClassSessionResponse> reschedule(UUID courseId, UUID id, RescheduleSessionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleException("Giờ kết thúc phải sau giờ bắt đầu");
        }
        var selected = find(courseId, id);
        moveSession(selected, request.startsAt(), request.endsAt());
        sessions.save(selected);
        if (request.shiftFollowing()) {
            var slots = request.weeklySlots().stream()
                    .sorted(Comparator.comparingInt(WeeklySlotRequest::dayOfWeek)).toList();
            var cursor = request.endsAt();
            for (var following : sessions.findByCourseIdOrderBySessionNo(courseId)) {
                if (following.getSessionNo() <= selected.getSessionNo()
                        || following.getStatus() == SessionStatus.COMPLETED) continue;
                var occurrence = nextOccurrence(cursor, slots);
                moveSession(following, occurrence.startsAt(), occurrence.endsAt());
                sessions.save(following);
                cursor = occurrence.endsAt();
            }
        }
        return list(courseId);
    }

    @Transactional
    public ClassSessionResponse update(UUID courseId, UUID id, UpsertClassSessionRequest request) {
        validate(request);
        var value = find(courseId, id);
        if (!value.getSessionNo().equals(request.sessionNo())
                && sessions.existsByCourseIdAndSessionNo(courseId, request.sessionNo())) {
            throw new ConflictException("Số thứ tự session đã tồn tại trong khóa học");
        }
        apply(value, request);
        value = sessions.save(value);
        replaceItems(value, request);
        return response(value);
    }

    @Transactional
    public void delete(UUID courseId, UUID id) {
        var value = find(courseId, id);
        if (value.getStatus() == SessionStatus.COMPLETED) {
            throw new BusinessRuleException("Không thể xóa session đã hoàn thành");
        }
        sessions.delete(value);
    }

    private void validate(UpsertClassSessionRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleException("Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (request.items() != null && request.items().stream()
                .filter(item -> !"MATERIAL".equals(item.itemType())).count() > 10) {
            throw new BusinessRuleException("Một session chỉ được có tối đa 10 bài tập hoặc bài test");
        }
        if (request.items() != null) {
            request.items().forEach(item -> {
                if ("ASSIGNMENT".equals(item.itemType()) && item.sourceTestId() != null) {
                    throw new BusinessRuleException("Bài tập không thể liên kết với kho đề test");
                }
                if ("TEST".equals(item.itemType()) && item.sourceAssignmentId() != null) {
                    throw new BusinessRuleException("Bài test không thể liên kết với kho bài tập");
                }
                if ("TEST".equals(item.itemType())
                        && (item.sourceResourceId() != null || item.sourceExerciseTemplateId() != null)) {
                    throw new BusinessRuleException("Bài test chỉ được liên kết với kho đề test");
                }
                if ("MATERIAL".equals(item.itemType())
                        && (item.sourceAssignmentId() != null || item.sourceTestId() != null
                        || item.sourceExerciseTemplateId() != null)) {
                    throw new BusinessRuleException("Tài liệu buổi học chỉ được liên kết với kho tài liệu");
                }
                if ("ASSIGNMENT".equals(item.itemType()) && item.sourceResourceId() != null) {
                    throw new BusinessRuleException("Bài tập không thể liên kết với kho tài liệu");
                }
            });
        }
    }

    private void apply(ClassSession value, UpsertClassSessionRequest request) {
        value.setSessionNo(request.sessionNo());
        value.setTitle(blank(request.title()));
        value.setStartsAt(request.startsAt());
        value.setEndsAt(request.endsAt());
        value.setZoomMeetingId(blank(request.zoomMeetingId()));
        value.setZoomUrl(blank(request.zoomUrl()));
        value.setStatus(request.status());
        value.setNotes(blank(request.notes()));
        value.setPhaseName(blank(request.phaseName()));
        value.setContent(blank(request.content()));
        value.setTeacherId(request.teacherId());
    }

    private void replaceItems(ClassSession session, UpsertClassSessionRequest request) {
        sessionItems.deleteBySessionId(session.getId());
        if (request.items() == null || request.items().isEmpty()) return;
        for (int index = 0; index < request.items().size(); index++) {
            var input = request.items().get(index);
            var value = new CourseSessionItem();
            value.setSessionId(session.getId());
            value.setItemType(input.itemType());
            value.setTitle(input.title().trim());
            value.setDescription(blank(input.description()));
            value.setSourceAssignmentId(input.sourceAssignmentId());
            value.setSourceTestId(input.sourceTestId());
            value.setSourceResourceId(input.sourceResourceId());
            value.setSourceExerciseTemplateId(input.sourceExerciseTemplateId());
            value.setDeadlineAt("MATERIAL".equals(input.itemType()) ? input.deadlineAt()
                    : input.deadlineAt() == null ? session.getEndsAt().plusDays(2) : input.deadlineAt());
            value.setIsRequired(input.required() == null ? !"MATERIAL".equals(input.itemType()) : input.required());
            value.setVisibility(input.visibility() == null ? "STUDENT" : input.visibility());
            value.setDisplayOrder((short) index);
            sessionItems.save(value);
        }
    }

    private void moveSession(ClassSession session, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        var difference = Duration.between(session.getEndsAt(), endsAt);
        session.setStartsAt(startsAt);
        session.setEndsAt(endsAt);
        sessionItems.findBySessionIdOrderByDisplayOrderAscCreatedAtAsc(session.getId()).forEach(item -> {
            if (item.getDeadlineAt() != null) {
                item.setDeadlineAt(item.getDeadlineAt().plus(difference));
            }
            sessionItems.save(item);
        });
    }

    private Occurrence nextOccurrence(OffsetDateTime after, List<WeeklySlotRequest> slots) {
        var zone = ZoneOffset.ofHours(7);
        var afterLocal = after.atZoneSameInstant(zone).toLocalDateTime();
        Occurrence best = null;
        for (var slot : slots) {
            var targetDay = DayOfWeek.of(slot.dayOfWeek());
            var date = afterLocal.toLocalDate().with(TemporalAdjusters.nextOrSame(targetDay));
            var start = LocalDateTime.of(date, slot.startsAt());
            if (!start.isAfter(afterLocal)) start = start.plusWeeks(1);
            var end = LocalDateTime.of(start.toLocalDate(), slot.endsAt());
            if (!end.isAfter(start)) end = end.plusDays(1);
            var candidate = new Occurrence(start.atOffset(zone), end.atOffset(zone));
            if (best == null || candidate.startsAt().isBefore(best.startsAt())) best = candidate;
        }
        if (best == null) throw new BusinessRuleException("Cần chọn ít nhất một buổi học trong tuần");
        return best;
    }

    private record Occurrence(OffsetDateTime startsAt, OffsetDateTime endsAt) {}

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireCourse(UUID id) {
        if (!courses.existsById(id)) throw new ResourceNotFoundException("Không tìm thấy khóa học");
    }

    private ClassSession find(UUID courseId, UUID id) {
        return sessions.findByIdAndCourseId(id, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy session"));
    }

    private ClassSessionResponse response(ClassSession value) {
        var items = sessionItems.findBySessionIdOrderByDisplayOrderAscCreatedAtAsc(value.getId()).stream()
                .map(item -> new CourseSessionItemResponse(item.getId(), item.getItemType(), item.getTitle(),
                        item.getDescription(), item.getSourceAssignmentId(), item.getSourceTestId(),
                        item.getSourceResourceId(), item.getSourceExerciseTemplateId(),
                        item.getDeadlineAt(), item.getDisplayOrder(), Boolean.TRUE.equals(item.getIsRequired()),
                        item.getVisibility()))
                .toList();
        String teacherName = value.getTeacherRef() == null ? null : value.getTeacherRef().getFullName();
        return new ClassSessionResponse(value.getId(), value.getCourseId(), value.getSessionNo(), value.getTitle(),
                value.getStartsAt(), value.getEndsAt(), value.getZoomMeetingId(), value.getZoomUrl(),
                value.getStatus(), value.getNotes(), value.getPhaseName(), value.getContent(),
                value.getTeacherId(), teacherName, items);
    }
}
