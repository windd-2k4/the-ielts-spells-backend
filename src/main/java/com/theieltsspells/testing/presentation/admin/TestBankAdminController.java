package com.theieltsspells.testing.presentation.admin;

import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.TestBankApplicationService;
import com.theieltsspells.testing.application.dto.TestBankRequest;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import com.theieltsspells.testing.application.dto.TestStatusRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/test-bank")
@RequiredArgsConstructor
@Tag(name = "Admin - Test bank")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'manager', 'teacher')")
public class TestBankAdminController {
    private final TestBankApplicationService service;
    @GetMapping public PageResponse<TestBankResponse> list(@RequestParam(required = false) String query,
            @RequestParam(required = false) String purpose, @RequestParam(required = false) SkillType skill,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) { return service.list(query, purpose, skill, status, page, size); }
    @GetMapping("/{id}") public TestBankResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping public ResponseEntity<TestBankResponse> create(@Valid @RequestBody TestBankRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        var result = service.create(request, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/admin/test-bank/" + result.id())).body(result);
    }
    @PutMapping("/{id}") public TestBankResponse update(@PathVariable UUID id, @Valid @RequestBody TestBankRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/status") public TestBankResponse status(@PathVariable UUID id, @Valid @RequestBody TestStatusRequest request) { return service.changeStatus(id, request.status()); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> archive(@PathVariable UUID id) { service.archive(id); return ResponseEntity.noContent().build(); }
}
