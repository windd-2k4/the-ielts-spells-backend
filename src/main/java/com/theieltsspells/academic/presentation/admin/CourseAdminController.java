package com.theieltsspells.academic.presentation.admin;

import com.theieltsspells.academic.application.CourseApplicationService;
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
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
@Tag(name = "Admin - Courses")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'manager')")
public class CourseAdminController {
    private final CourseApplicationService service;

    @PostMapping
    @Operation(summary = "Tạo khóa học")
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/courses/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết khóa học")
    public CourseResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping
    @Operation(summary = "Danh sách khóa học")
    public PageResponse<CourseResponse> list(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(service.list(active, pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật khóa học")
    public CourseResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Ngừng hoạt động khóa học")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
