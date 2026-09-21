package com.theieltsspells.testing.presentation.student;

import com.theieltsspells.testing.application.StudentReadingDeliveryService;
import com.theieltsspells.testing.application.dto.ReadingAttemptResultResponse;
import com.theieltsspells.testing.application.dto.ReadingAnnotationResponse;
import com.theieltsspells.testing.application.dto.SaveReadingAnnotationRequest;
import com.theieltsspells.testing.application.dto.SaveReadingResponsesRequest;
import com.theieltsspells.testing.application.dto.StudentReadingAssignmentResponse;
import com.theieltsspells.testing.application.dto.StudentReadingAttemptResponse;
import com.theieltsspells.testing.application.dto.StudentReadingCatalogItemResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/reading")
@RequiredArgsConstructor
@Tag(name = "Student - Reading tests")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.attempt')")
public class StudentReadingTestController {

    private final StudentReadingDeliveryService service;

    @GetMapping("/assignments")
    public List<StudentReadingAssignmentResponse> assignments(@AuthenticationPrincipal Jwt jwt) {
        return service.listAssignments(studentId(jwt));
    }

    @GetMapping("/catalog")
    public List<StudentReadingCatalogItemResponse> catalog(@AuthenticationPrincipal Jwt jwt) {
        return service.listPublishedTests(studentId(jwt));
    }

    @PostMapping("/catalog/{testVersionId}/attempts")
    public StudentReadingAttemptResponse startOrResumeSelfPractice(@PathVariable UUID testVersionId,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        return service.startOrResumeSelfPractice(testVersionId, studentId(jwt));
    }

    @PostMapping("/assignments/{assignmentId}/attempts")
    public StudentReadingAttemptResponse startOrResume(@PathVariable UUID assignmentId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return service.startOrResume(assignmentId, studentId(jwt));
    }

    @GetMapping("/attempts/{attemptId}")
    public StudentReadingAttemptResponse attempt(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return service.getAttempt(attemptId, studentId(jwt));
    }

    @PutMapping("/attempts/{attemptId}/responses")
    public StudentReadingAttemptResponse saveResponses(@PathVariable UUID attemptId,
                                                       @Valid @RequestBody SaveReadingResponsesRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return service.saveResponses(attemptId, request, studentId(jwt));
    }

    @PutMapping("/attempts/{attemptId}/annotations/{annotationId}")
    public ReadingAnnotationResponse saveAnnotation(@PathVariable UUID attemptId,
                                                     @PathVariable UUID annotationId,
                                                     @Valid @RequestBody SaveReadingAnnotationRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return service.saveAnnotation(attemptId, annotationId, request, studentId(jwt));
    }

    @DeleteMapping("/attempts/{attemptId}/annotations/{annotationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnnotation(@PathVariable UUID attemptId,
                                 @PathVariable UUID annotationId,
                                 @AuthenticationPrincipal Jwt jwt) {
        service.deleteAnnotation(attemptId, annotationId, studentId(jwt));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ReadingAttemptResultResponse submit(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return service.submit(attemptId, studentId(jwt));
    }

    @GetMapping("/attempts/{attemptId}/result")
    @PreAuthorize("@permissionPolicy.has(authentication, 'assessment.result.read')")
    public ReadingAttemptResultResponse result(@PathVariable UUID attemptId, @AuthenticationPrincipal Jwt jwt) {
        return service.getResult(attemptId, studentId(jwt));
    }

    private UUID studentId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
