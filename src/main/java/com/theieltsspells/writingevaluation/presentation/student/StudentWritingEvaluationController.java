package com.theieltsspells.writingevaluation.presentation.student;

import com.theieltsspells.writingevaluation.application.WritingEvaluationApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/writing")
@RequiredArgsConstructor
@Tag(name = "Student - Writing evaluation")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.attempt')")
public class StudentWritingEvaluationController {
    private final WritingEvaluationApplicationService service;

    @PostMapping("/attempts/{attemptId}/ai-evaluation")
    public Map<String, Integer> requestAi(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("queuedCount", service.requestAi(attemptId, UUID.fromString(jwt.getSubject())));
    }
}
