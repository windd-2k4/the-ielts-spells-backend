package com.theieltsspells.academic.presentation.admin;

import com.theieltsspells.academic.application.ScheduleTemplateApplicationService;
import com.theieltsspells.academic.application.dto.ScheduleTemplateResponse;
import com.theieltsspells.academic.application.dto.UpsertScheduleTemplateRequest;
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
@RequestMapping("/api/v1/admin/schedule-templates")
@RequiredArgsConstructor
@Tag(name = "Admin - Schedule templates")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'manager')")
public class ScheduleTemplateAdminController {
    private final ScheduleTemplateApplicationService service;
    @GetMapping public List<ScheduleTemplateResponse> list(@RequestParam String skillPair) { return service.list(skillPair); }
    @PostMapping public ScheduleTemplateResponse create(@Valid @RequestBody UpsertScheduleTemplateRequest request) { return service.create(request); }
    @PutMapping("/{id}") public ScheduleTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody UpsertScheduleTemplateRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
