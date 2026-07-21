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
@PreAuthorize("hasAnyAuthority('admin', 'manager', 'admissions')")
public class EnrollmentAdminController {
    private final EnrollmentApplicationService service;

    @PostMapping
    @Operation(summary = "Ghi danh học viên vào lớp")
    public ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollStudentRequest request) {
        var result = service.enroll(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/enrollments/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public EnrollmentResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping
    @Operation(summary = "Danh sách ghi danh theo lớp hoặc học viên")
    public PageResponse<EnrollmentResponse> list(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID studentId,
            @PageableDefault(size = 20, sort = "enrolledAt") Pageable pageable) {
        var page = classId != null ? service.listByClass(classId, pageable)
                : studentId != null ? service.listByStudent(studentId, pageable)
                : service.list(pageable);
        return PageResponse.from(page);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cập nhật trạng thái ghi danh")
    public EnrollmentResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateEnrollmentRequest request) {
        return service.update(id, request);
    }
}
