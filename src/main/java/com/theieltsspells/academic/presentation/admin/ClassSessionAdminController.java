package com.theieltsspells.academic.presentation.admin;

import com.theieltsspells.academic.application.ClassSessionApplicationService;
import com.theieltsspells.academic.application.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/courses/{courseId}/sessions")
@RequiredArgsConstructor
@Tag(name = "Admin - Class sessions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'manager', 'teacher')")
public class ClassSessionAdminController {
    private final ClassSessionApplicationService service;
    @GetMapping public List<ClassSessionResponse> list(@PathVariable UUID courseId) { return service.list(courseId); }
    @PostMapping @PreAuthorize("hasAnyAuthority('admin', 'manager')") public ClassSessionResponse create(@PathVariable UUID courseId, @Valid @RequestBody UpsertClassSessionRequest request) { return service.create(courseId, request); }
    @PostMapping("/bulk") @PreAuthorize("hasAnyAuthority('admin', 'manager')") public List<ClassSessionResponse> bulkCreate(@PathVariable UUID courseId, @Valid @RequestBody BulkCreateSessionsRequest request) { return service.bulkCreate(courseId, request); }
    @PutMapping("/{id}") @PreAuthorize("hasAnyAuthority('admin', 'manager')") public ClassSessionResponse update(@PathVariable UUID courseId, @PathVariable UUID id, @Valid @RequestBody UpsertClassSessionRequest request) { return service.update(courseId, id, request); }
    @PostMapping("/{id}/reschedule") @PreAuthorize("hasAnyAuthority('admin', 'manager')") public List<ClassSessionResponse> reschedule(@PathVariable UUID courseId, @PathVariable UUID id, @Valid @RequestBody RescheduleSessionRequest request) { return service.reschedule(courseId, id, request); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyAuthority('admin', 'manager')") public ResponseEntity<Void> delete(@PathVariable UUID courseId, @PathVariable UUID id) { service.delete(courseId, id); return ResponseEntity.noContent().build(); }
}
