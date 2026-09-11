package com.theieltsspells.testing.presentation.admin;

import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.security.PermissionPolicy;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.TestBankApplicationService;
import com.theieltsspells.testing.application.dto.TestBankRequest;
import com.theieltsspells.testing.application.dto.TestBankResponse;
import com.theieltsspells.testing.application.dto.TestRevisionRequest;
import com.theieltsspells.testing.application.dto.TestStatusRequest;
import com.theieltsspells.testing.application.dto.TestValidationResponse;
import com.theieltsspells.testing.application.dto.TestVersionResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
public class TestBankAdminController {
    private final TestBankApplicationService service;
    private final PermissionPolicy permissionPolicy;

    @GetMapping
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public PageResponse<TestBankResponse> list(@RequestParam(required = false) String query,
            @RequestParam(required = false) SkillType skill,
            @RequestParam(required = false) String status, @RequestParam(required = false) String testType,
            @RequestParam(required = false) String format, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) { return service.list(query, skill, status, testType, format, page, size); }
    @GetMapping("/{id}")
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public TestBankResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.create')")
    public ResponseEntity<TestBankResponse> create(@Valid @RequestBody TestBankRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        var result = service.create(request, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.created(URI.create("/api/v1/admin/test-bank/" + result.id())).body(result);
    }
    @PutMapping("/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.update_own')")
    public TestBankResponse update(@PathVariable UUID id, @Valid @RequestBody TestBankRequest request,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return service.update(id, request, UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }
    @GetMapping("/{id}/validation")
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public TestValidationResponse validate(@PathVariable UUID id) {
        return service.validateDraft(id);
    }
    @GetMapping("/{id}/versions")
    @PreAuthorize("@permissionPolicy.canViewTestBank(authentication)")
    public java.util.List<TestVersionResponse> versions(@PathVariable UUID id) {
        return service.listVersions(id);
    }
    @PostMapping("/{id}/revisions")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.update_own')")
    public TestBankResponse createRevision(@PathVariable UUID id,
            @Valid @RequestBody TestRevisionRequest request, @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.createRevision(id, request.draftRevision(), UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }
    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionPolicy.canChangeTestStatus(authentication, #request.status())")
    public TestBankResponse status(@PathVariable UUID id, @Valid @RequestBody TestStatusRequest request,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return service.changeStatus(id, request.status(), request.draftRevision(), UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.draft.update_own')")
    public ResponseEntity<Void> archive(@PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        service.archive(id, UUID.fromString(jwt.getSubject()), isModerator(authentication));
        return ResponseEntity.noContent().build();
    }

    private boolean isModerator(Authentication authentication) {
        return permissionPolicy.isTestModerator(authentication);
    }
}
