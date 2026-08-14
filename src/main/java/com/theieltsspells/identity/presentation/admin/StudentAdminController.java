package com.theieltsspells.identity.presentation.admin;

import com.theieltsspells.identity.application.StudentQueryService;
import com.theieltsspells.identity.application.dto.StudentDetailResponse;
import com.theieltsspells.identity.application.dto.StudentLifecycleStatus;
import com.theieltsspells.identity.application.dto.StudentSearchResponse;
import com.theieltsspells.identity.application.dto.UpdateStudentRequest;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/students")
@RequiredArgsConstructor
@Tag(name = "Admin - Students")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'manager', 'admissions')")
public class StudentAdminController {
    private final StudentQueryService service;

    @GetMapping
    @Operation(summary = "Tìm học viên theo tên, email hoặc số điện thoại")
    public PageResponse<StudentSearchResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) StudentLifecycleStatus lifecycle,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.search(q, active, lifecycle, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem hồ sơ chi tiết học viên")
    public StudentDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cập nhật hồ sơ học viên")
    public StudentDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStudentRequest request) {
        return service.update(id, request);
    }
}
