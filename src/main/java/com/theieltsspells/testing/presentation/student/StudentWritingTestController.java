package com.theieltsspells.testing.presentation.student;

import com.theieltsspells.testing.application.StudentWritingDeliveryService;
import com.theieltsspells.testing.application.dto.SaveWritingResponsesRequest;
import com.theieltsspells.testing.application.dto.StudentWritingAssignmentResponse;
import com.theieltsspells.testing.application.dto.StudentWritingAttemptResponse;
import com.theieltsspells.testing.application.dto.WritingAttemptResultResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/writing")
@RequiredArgsConstructor
@Tag(name = "Student - Writing tests")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.attempt')")
public class StudentWritingTestController {
    private final StudentWritingDeliveryService service;

    @GetMapping("/assignments")
    public List<StudentWritingAssignmentResponse> assignments(@AuthenticationPrincipal Jwt jwt) {
        return service.listAssignments(studentId(jwt));
    }

    @PostMapping("/catalog/{testVersionId}/attempts")
    public StudentWritingAttemptResponse startSelfPractice(@PathVariable UUID testVersionId,
                                                           @RequestParam(defaultValue = "false") boolean restart,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return service.startOrResumeSelfPractice(testVersionId, studentId(jwt), restart);
    }

    @PostMapping("/assignments/{assignmentId}/attempts")
    public StudentWritingAttemptResponse startAssignment(@PathVariable UUID assignmentId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return service.startOrResume(assignmentId, studentId(jwt));
    }

    @GetMapping("/attempts/{attemptId}")
    public StudentWritingAttemptResponse attempt(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return service.getAttempt(attemptId, studentId(jwt));
    }

    @PutMapping("/attempts/{attemptId}/responses")
    public StudentWritingAttemptResponse save(@PathVariable UUID attemptId,
                                              @Valid @RequestBody SaveWritingResponsesRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return service.saveResponses(attemptId, request, studentId(jwt));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public WritingAttemptResultResponse submit(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return service.submit(attemptId, studentId(jwt));
    }

    @GetMapping("/attempts/{attemptId}/result")
    @PreAuthorize("@permissionPolicy.has(authentication, 'assessment.result.read')")
    public WritingAttemptResultResponse result(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return service.getResult(attemptId, studentId(jwt));
    }

    private UUID studentId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
