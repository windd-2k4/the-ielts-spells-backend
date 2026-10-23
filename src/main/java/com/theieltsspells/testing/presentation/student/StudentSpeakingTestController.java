package com.theieltsspells.testing.presentation.student;

import com.theieltsspells.testing.application.SpeakingVocabService;
import com.theieltsspells.testing.application.StudentSpeakingDeliveryService;
import com.theieltsspells.testing.application.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/speaking")
@RequiredArgsConstructor
@Tag(name = "Student - Speaking tests")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.attempt')")
public class StudentSpeakingTestController {

    private final StudentSpeakingDeliveryService deliveryService;
    private final SpeakingVocabService vocabService;

    @GetMapping("/assignments")
    public List<StudentWritingAssignmentResponse> assignments(@AuthenticationPrincipal Jwt jwt) {
        return deliveryService.listAssignments(studentId(jwt));
    }

    @PostMapping("/catalog/{testVersionId}/attempts")
    public StudentSpeakingAttemptResponse startSelfPractice(
            @PathVariable UUID testVersionId,
            @RequestParam(defaultValue = "false") boolean restart,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return deliveryService.startOrResumeSelfPractice(testVersionId, studentId(jwt), restart);
    }

    @PostMapping("/assignments/{assignmentId}/attempts")
    public StudentSpeakingAttemptResponse startAssignment(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return deliveryService.startOrResume(assignmentId, studentId(jwt));
    }

    @GetMapping("/attempts/{attemptId}")
    public StudentSpeakingAttemptResponse attempt(
            @PathVariable UUID attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return deliveryService.getAttempt(attemptId, studentId(jwt));
    }

    @PostMapping("/attempts/{attemptId}/resume")
    public StudentSpeakingAttemptResponse resume(
            @PathVariable UUID attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return deliveryService.resumeAttempt(attemptId, studentId(jwt));
    }

    @PostMapping("/attempts/{attemptId}/pause")
    public void pause(
            @PathVariable UUID attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        deliveryService.pauseAttempt(attemptId, studentId(jwt));
    }

    @PutMapping("/attempts/{attemptId}/responses")
    public StudentSpeakingAttemptResponse save(
            @PathVariable UUID attemptId,
            @Valid @RequestBody SaveSpeakingResponsesRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return deliveryService.saveResponses(attemptId, request, studentId(jwt));
    }

    @PostMapping("/attempts/{attemptId}/ai-evaluation")
    public Map<String, Integer> requestAi(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("queuedCount", deliveryService.requestAi(attemptId, studentId(jwt)));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public SpeakingAttemptResultResponse submit(
            @PathVariable UUID attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return deliveryService.submit(attemptId, studentId(jwt));
    }

    @GetMapping("/attempts/{attemptId}/result")
    @PreAuthorize("@permissionPolicy.has(authentication, 'assessment.result.read')")
    public SpeakingAttemptResultResponse result(
            @PathVariable UUID attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return deliveryService.getResult(attemptId, studentId(jwt));
    }

    @PostMapping("/vocab-lookup")
    public SpeakingVocabLookupResponse vocabLookup(
            @Valid @RequestBody SpeakingVocabLookupRequest request
    ) {
        return vocabService.lookup(request);
    }

    private UUID studentId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
