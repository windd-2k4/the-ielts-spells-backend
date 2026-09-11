package com.theieltsspells.academic.presentation.admin;

import com.theieltsspells.academic.application.CourseApplicationService;
import com.theieltsspells.academic.application.CourseStudentSupportApplicationService;
import com.theieltsspells.academic.application.CourseTeacherApplicationService;
import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
@Tag(name = "Admin - Courses")
@SecurityRequirement(name = "bearerAuth")
public class CourseAdminController {
    private final CourseApplicationService service;
    private final CourseStudentSupportApplicationService studentSupports;
    private final CourseTeacherApplicationService teachers;

    @PostMapping
    @PreAuthorize("@permissionPolicy.has(authentication, 'course.manage')")
    @Operation(summary = "Tạo khóa học")
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #id, 'course.read')")
    @Operation(summary = "Xem chi tiết khóa học")
    public CourseResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping
    @PreAuthorize("@permissionPolicy.hasAdministrativeScope(authentication, 'course.read')")
    @Operation(summary = "Danh sách khóa học")
    public PageResponse<CourseResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) ClassStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(status == null ? service.list(active, pageable) : service.listByStatus(status, pageable));
    }

    @GetMapping("/upcoming")
    @PreAuthorize("@permissionPolicy.hasAdministrativeScope(authentication, 'course.read')")
    @Operation(summary = "Các khóa học sắp khai giảng")
    public List<CourseResponse> upcoming(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.upcoming(from, to);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'course.manage')")
    @Operation(summary = "Cập nhật khóa học")
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'course.manage')")
    @Operation(summary = "Ngừng hoạt động khóa học")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/teachers")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #id, 'course.read')")
    @Operation(summary = "Danh sách giáo viên chính và giáo viên dạy thay của khóa học")
    public List<CourseTeacherAssignmentResponse> listTeachers(@PathVariable UUID id) {
        return teachers.listForCourse(id);
    }

    @PutMapping("/{id}/teachers/primary")
    @PreAuthorize("@permissionPolicy.has(authentication, 'course.teacher.assign')")
    @Operation(summary = "Phân công hoặc thay giáo viên chính của khóa học")
    public List<CourseTeacherAssignmentResponse> setPrimaryTeacher(
            @PathVariable UUID id,
            @Valid @RequestBody SetPrimaryCourseTeacherRequest request
    ) {
        return teachers.setPrimary(id, request);
    }

    @GetMapping("/{id}/student-supports")
    @PreAuthorize("@permissionPolicy.has(authentication, 'course.student_support.assign')")
    @Operation(summary = "Danh sách Student Support được phân công cho khóa học")
    public List<CourseStudentSupportAssignmentResponse> listStudentSupports(@PathVariable UUID id) {
        return studentSupports.listForCourse(id);
    }

    @PutMapping("/{id}/student-supports")
    @PreAuthorize("@permissionPolicy.has(authentication, 'course.student_support.assign')")
    @Operation(summary = "Thay toàn bộ phân công Student Support cho khóa học")
    public List<CourseStudentSupportAssignmentResponse> replaceStudentSupports(
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceCourseStudentSupportsRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return studentSupports.replaceForCourse(id, request, UUID.fromString(jwt.getSubject()));
    }
}
