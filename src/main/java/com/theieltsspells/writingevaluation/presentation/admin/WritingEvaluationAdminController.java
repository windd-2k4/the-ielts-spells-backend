package com.theieltsspells.writingevaluation.presentation.admin;

import com.theieltsspells.writingevaluation.application.WritingEvaluationApplicationService;
import com.theieltsspells.writingevaluation.application.dto.ReviewWritingEvaluationRequest;
import com.theieltsspells.writingevaluation.application.dto.WritingEvaluationResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/writing-evaluations")
@RequiredArgsConstructor
@Tag(name = "Admin - Writing evaluation")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.grade')")
public class WritingEvaluationAdminController {
    private final WritingEvaluationApplicationService service;

    @GetMapping
    public List<WritingEvaluationResponse> list(@RequestParam(required = false) String status) { return service.list(status); }

    @GetMapping("/{id}")
    public WritingEvaluationResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}/review")
    public WritingEvaluationResponse review(@PathVariable UUID id,
                                            @Valid @RequestBody ReviewWritingEvaluationRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return service.review(id, request, UUID.fromString(jwt.getSubject()));
    }
}
