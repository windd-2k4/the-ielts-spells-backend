package com.theieltsspells.assignment.presentation.admin;

import com.theieltsspells.assignment.application.AssignmentApplicationService;
import com.theieltsspells.assignment.application.dto.AssignmentResponse;
import com.theieltsspells.assignment.application.dto.AssignmentStudentExtensionResponse;
import com.theieltsspells.assignment.application.dto.AssignmentContactLogResponse;
import com.theieltsspells.assignment.application.dto.BulkAssignmentReminderRequest;
import com.theieltsspells.assignment.application.dto.BulkAssignmentReminderResponse;
import com.theieltsspells.assignment.application.dto.CreateAssignmentRequest;
import com.theieltsspells.assignment.application.dto.GrantSubmissionExtensionRequest;
import com.theieltsspells.assignment.application.dto.ReviewSubmissionRequest;
import com.theieltsspells.assignment.application.dto.StudentSubmissionResponse;
import com.theieltsspells.assignment.application.dto.UpdateAssignmentSettingsRequest;
import com.theieltsspells.shared.security.PermissionPolicy;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/courses/{courseId}/activities/{classActivityId}")
@RequiredArgsConstructor
@Tag(name = "Admin - Course assignments")
@SecurityRequirement(name = "bearerAuth")
public class AssignmentAdminController {

    private final AssignmentApplicationService service;
    private final PermissionPolicy permissionPolicy;

    @PostMapping("/assignment")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public AssignmentResponse create(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @Valid @RequestBody CreateAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.create(courseId, classActivityId, request, UUID.fromString(jwt.getSubject()),
                permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication));
    }

    @GetMapping("/assignment")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public AssignmentResponse getAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId) {
        return service.assignmentForActivity(courseId, classActivityId);
    }

    @PatchMapping("/assignment")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public AssignmentResponse updateAssignment(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @Valid @RequestBody UpdateAssignmentSettingsRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.updateSettings(courseId, classActivityId, request, UUID.fromString(jwt.getSubject()),
                permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication));
    }

    @GetMapping("/assignment/students/{studentId}/extension")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public AssignmentStudentExtensionResponse studentExtension(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID studentId) {
        return service.assignmentStudentExtension(courseId, classActivityId, studentId);
    }

    @PatchMapping("/assignment/students/{studentId}/extension")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public AssignmentStudentExtensionResponse updateStudentExtension(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID studentId,
            @Valid @RequestBody GrantSubmissionExtensionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.grantAssignmentStudentExtension(courseId, classActivityId, studentId, request,
                UUID.fromString(jwt.getSubject()), permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication));
    }

    @PostMapping("/reminders")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public BulkAssignmentReminderResponse remind(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @Valid @RequestBody BulkAssignmentReminderRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.sendBulkReminder(courseId, classActivityId, request, UUID.fromString(jwt.getSubject()),
                permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication));
    }

    @GetMapping("/contact-logs")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public List<AssignmentContactLogResponse> contactLogs(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.contactLogs(courseId, classActivityId, UUID.fromString(jwt.getSubject()),
                permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication));
    }

    @GetMapping("/submissions")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public List<StudentSubmissionResponse> submissions(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId) {
        return service.submissions(courseId, classActivityId);
    }

    @PatchMapping("/submissions/{submissionId}/feedback")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public StudentSubmissionResponse review(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID submissionId,
            @Valid @RequestBody ReviewSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.review(courseId, classActivityId, submissionId, request,
                UUID.fromString(jwt.getSubject()), permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication));
    }

    @PatchMapping("/submissions/{submissionId}/extension")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public StudentSubmissionResponse extension(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID submissionId,
            @Valid @RequestBody GrantSubmissionExtensionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.grantExtension(courseId, classActivityId, submissionId, request,
                UUID.fromString(jwt.getSubject()), permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication));
    }

    @GetMapping("/submissions/{submissionId}/audio")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public ResponseEntity<InputStreamResource> audio(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID submissionId) {
        var audio = service.openAudio(courseId, classActivityId, submissionId, null);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(audio.mimeType()); }
        catch (IllegalArgumentException ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        var response = ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(audio.originalName() == null ? "speaking-audio" : audio.originalName(), StandardCharsets.UTF_8)
                        .build().toString());
        if (audio.sizeBytes() != null) response = response.contentLength(audio.sizeBytes());
        return response.body(new InputStreamResource(audio.inputStream()));
    }

    @GetMapping("/submissions/{submissionId}/audio/{fileId}")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public ResponseEntity<InputStreamResource> audioFile(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID submissionId,
            @PathVariable UUID fileId) {
        var audio = service.openAudio(courseId, classActivityId, submissionId, fileId, null);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(audio.mimeType()); }
        catch (IllegalArgumentException ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        var response = ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(audio.originalName() == null ? "speaking-audio" : audio.originalName(), StandardCharsets.UTF_8)
                        .build().toString());
        if (audio.sizeBytes() != null) response = response.contentLength(audio.sizeBytes());
        return response.body(new InputStreamResource(audio.inputStream()));
    }
}
