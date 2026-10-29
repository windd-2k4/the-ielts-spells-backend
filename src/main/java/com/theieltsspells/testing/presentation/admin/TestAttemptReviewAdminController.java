package com.theieltsspells.testing.presentation.admin;

import com.theieltsspells.shared.security.PermissionPolicy;
import com.theieltsspells.testing.application.TestAttemptReviewService;
import com.theieltsspells.testing.application.dto.TestAttemptInsightsResponse;
import com.theieltsspells.testing.application.dto.BulkTestAssignmentReminderRequest;
import com.theieltsspells.testing.application.dto.BulkTestAssignmentReminderResponse;
import com.theieltsspells.testing.application.dto.TestAttemptReviewResponse;
import com.theieltsspells.testing.application.dto.TestAttemptSummaryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/courses/{courseId}/test-attempts")
@RequiredArgsConstructor
@Tag(name = "Admin - Test attempt review")
@SecurityRequirement(name = "bearerAuth")
public class TestAttemptReviewAdminController {

    private final TestAttemptReviewService service;
    private final PermissionPolicy permissionPolicy;

    @GetMapping
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public List<TestAttemptSummaryResponse> list(@PathVariable UUID courseId,
                                                  @RequestParam(required = false) UUID sessionId,
                                                  @RequestParam(required = false) String skill,
                                                  @AuthenticationPrincipal Jwt jwt,
                                                  Authentication authentication) {
        return service.list(courseId, sessionId, skill, UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }

    @GetMapping("/insights")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public TestAttemptInsightsResponse insights(@PathVariable UUID courseId,
                                                @RequestParam(required = false) UUID sessionId,
                                                @RequestParam(required = false) String skill,
                                                @AuthenticationPrincipal Jwt jwt,
                                                Authentication authentication) {
        return service.insights(courseId, sessionId, skill, UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }

    @PostMapping("/reminders")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public BulkTestAssignmentReminderResponse remind(@PathVariable UUID courseId,
                                                      @jakarta.validation.Valid @RequestBody BulkTestAssignmentReminderRequest request,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      Authentication authentication) {
        return service.sendTestAssignmentReminder(courseId, request, UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }

    @GetMapping("/{attemptId}")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public TestAttemptReviewResponse detail(@PathVariable UUID courseId, @PathVariable UUID attemptId,
                                             @AuthenticationPrincipal Jwt jwt,
                                             Authentication authentication) {
        return service.detail(courseId, attemptId, UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }

    private boolean isModerator(Authentication authentication) {
        return permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication);
    }
}
