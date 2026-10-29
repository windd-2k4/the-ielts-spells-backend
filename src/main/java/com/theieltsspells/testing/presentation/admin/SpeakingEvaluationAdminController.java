package com.theieltsspells.testing.presentation.admin;

import com.theieltsspells.testing.application.SpeakingEvaluationAdminService;
import com.theieltsspells.testing.application.dto.AdminSpeakingEvaluationResponse;
import com.theieltsspells.testing.application.dto.ReviewSpeakingEvaluationRequest;
import com.theieltsspells.testing.application.dto.SpeakingAiEntitlementResponse;
import com.theieltsspells.testing.application.dto.UpdateSpeakingAiEntitlementRequest;
import com.theieltsspells.shared.security.PermissionPolicy;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/speaking-evaluations")
@RequiredArgsConstructor
@Tag(name = "Admin - Speaking evaluation")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.grade')")
public class SpeakingEvaluationAdminController {
    private final SpeakingEvaluationAdminService service;
    private final PermissionPolicy permissionPolicy;

    @GetMapping
    public List<AdminSpeakingEvaluationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                      Authentication authentication) {
        return service.list(UUID.fromString(jwt.getSubject()), permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @GetMapping("/{id}")
    public AdminSpeakingEvaluationResponse get(@PathVariable UUID id,
                                               @AuthenticationPrincipal Jwt jwt,
                                               Authentication authentication) {
        return service.get(id, UUID.fromString(jwt.getSubject()), permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @PutMapping("/{id}/review")
    public AdminSpeakingEvaluationResponse review(@PathVariable UUID id,
                                                  @Valid @RequestBody ReviewSpeakingEvaluationRequest request,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   Authentication authentication) {
        return service.review(id, request, UUID.fromString(jwt.getSubject()),
                permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @GetMapping("/entitlements")
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.update_academic')")
    public List<SpeakingAiEntitlementResponse> entitlements(@RequestParam(defaultValue = "") String q) {
        return service.listEntitlements(q);
    }

    @PutMapping("/entitlements/{studentId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.update_academic')")
    public SpeakingAiEntitlementResponse updateEntitlement(
            @PathVariable UUID studentId,
            @Valid @RequestBody UpdateSpeakingAiEntitlementRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updateEntitlement(studentId, request, UUID.fromString(jwt.getSubject()));
    }
}
