package com.theieltsspells.academic.presentation.admin;

import com.theieltsspells.academic.application.EnrollmentApplicationService;
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

@RestController
@RequestMapping("/api/v1/admin/enrollments")
@RequiredArgsConstructor
@Tag(name = "Admin - Enrollments")
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentAdminController {
    private final EnrollmentApplicationService service;

    @PostMapping
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.create')")
    @Operation(summary = "Ghi danh học viên vào lớp")
    public ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollStudentRequest request) {
        var result = service.enroll(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/enrollments/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionPolicy.hasAdministrativeScope(authentication, 'enrollment.read')")
    public EnrollmentResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping
    @PreAuthorize("@permissionPolicy.hasAdministrativeScope(authentication, 'enrollment.read')")
    @Operation(summary = "Danh sách ghi danh theo lớp hoặc học viên")
    public PageResponse<EnrollmentResponse> list(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID studentId,
            @PageableDefault(size = 20, sort = "enrolledAt") Pageable pageable) {
        var page = courseId != null ? service.listByCourse(courseId, pageable)
                : studentId != null ? service.listByStudent(studentId, pageable)
                : service.list(pageable);
        return PageResponse.from(page);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.status.manage')")
    @Operation(summary = "Cập nhật trạng thái ghi danh")
    public EnrollmentResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateEnrollmentRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/exam-plan")
    @PreAuthorize("@permissionPolicy.has(authentication, 'enrollment.exam_plan.manage')")
    @Operation(summary = "Cập nhật kế hoạch thi IELTS của học viên trong khóa")
    public EnrollmentResponse updateExamPlan(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateEnrollmentExamPlanRequest request) {
        return service.updateExamPlan(id, request);
    }
}
