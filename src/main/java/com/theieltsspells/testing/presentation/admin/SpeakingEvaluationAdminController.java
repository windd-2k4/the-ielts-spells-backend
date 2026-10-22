package com.theieltsspells.testing.presentation.admin;

import com.theieltsspells.testing.application.SpeakingEvaluationAdminService;
import com.theieltsspells.testing.application.dto.AdminSpeakingEvaluationResponse;
import com.theieltsspells.testing.application.dto.ReviewSpeakingEvaluationRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public List<AdminSpeakingEvaluationResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public AdminSpeakingEvaluationResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}/review")
    public AdminSpeakingEvaluationResponse review(@PathVariable UUID id,
                                                  @Valid @RequestBody ReviewSpeakingEvaluationRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return service.review(id, request, UUID.fromString(jwt.getSubject()));
    }
}
