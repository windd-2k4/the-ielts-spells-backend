package com.theieltsspells.academic.presentation.admin;

import com.theieltsspells.academic.application.ClassApplicationService;
import com.theieltsspells.academic.application.dto.*;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/classes")
@RequiredArgsConstructor
@Tag(name = "Admin - Classes")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'manager')")
public class ClassAdminController {
    private final ClassApplicationService service;

    @PostMapping
    @Operation(summary = "Tạo lớp học")
    public ResponseEntity<ClassResponse> create(@Valid @RequestBody CreateClassRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/classes/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public ClassResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping
    @Operation(summary = "Danh sách lớp, lọc theo khóa học hoặc trạng thái")
    public PageResponse<ClassResponse> list(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) ClassStatus status,
            @PageableDefault(size = 20, sort = "startsOn") Pageable pageable) {
        var page = courseId != null ? service.listByCourse(courseId, pageable)
                : status != null ? service.listByStatus(status, pageable)
                : service.list(pageable);
        return PageResponse.from(page);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Lớp sắp khai giảng trong khoảng ngày")
    public List<ClassResponse> upcoming(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.upcoming(from, to);
    }

    @PutMapping("/{id}")
    public ClassResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateClassRequest request) {
        return service.update(id, request);
    }
}
