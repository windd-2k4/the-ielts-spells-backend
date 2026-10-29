package com.theieltsspells.studentportal.presentation;

import com.theieltsspells.studentportal.application.StudentPortalService;
import com.theieltsspells.studentportal.application.dto.StudentCourseResponse;
import com.theieltsspells.studentportal.application.dto.StudentCourseSessionResponse;
import com.theieltsspells.studentportal.application.dto.StudentExamPlanResponse;
import com.theieltsspells.studentportal.application.dto.StudentPortalOverviewResponse;
import com.theieltsspells.studentportal.application.dto.StudentPortfolioResponse;
import com.theieltsspells.studentportal.application.dto.StudentTargetBandResponse;
import com.theieltsspells.studentportal.application.dto.UpdateStudentExamPlanRequest;
import com.theieltsspells.studentportal.application.dto.UpdateStudentTargetBandRequest;
import com.theieltsspells.progress.application.StudentProgressApplicationService;
import com.theieltsspells.progress.application.dto.StudentProgressTaskResponse;
import com.theieltsspells.progress.application.dto.UpdateChecklistRequest;
import com.theieltsspells.progress.application.dto.UpdateProgressSummaryRequest;
import com.theieltsspells.assignment.application.AssignmentApplicationService;
import com.theieltsspells.assignment.application.dto.ReflectionRequest;
import com.theieltsspells.assignment.application.dto.StudentSubmissionRequest;
import com.theieltsspells.assignment.application.dto.StudentSubmissionResponse;
import com.theieltsspells.assignment.application.dto.StudentSubmissionRevisionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/portal")
@RequiredArgsConstructor
@Tag(name = "Student - Learning portal")
@SecurityRequirement(name = "bearerAuth")
public class StudentPortalController {

    private final StudentPortalService service;
    private final com.theieltsspells.studentportal.application.StudentCourseWorkspaceService workspaceService;
    private final StudentProgressApplicationService progressService;
    private final AssignmentApplicationService assignmentService;

    @GetMapping("/overview")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy dữ liệu tổng hợp thật cho góc học tập")
    public StudentPortalOverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return service.overview(studentId(jwt));
    }

    @GetMapping("/activity")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy hoạt động học tập đã nộp trong một tháng")
    public List<StudentPortalOverviewResponse.DailyActivity> activity(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.activity(studentId(jwt), month);
    }

    @GetMapping("/courses")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy danh sách khóa học hiển thị cho học viên")
    public List<StudentCourseResponse> courses(@AuthenticationPrincipal Jwt jwt) {
        return service.courses(studentId(jwt));
    }

    @GetMapping("/courses/{courseId}/sessions")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy lịch học của khóa học mà học viên đã ghi danh")
    public List<StudentCourseSessionResponse> courseSessions(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.courseSessions(studentId(jwt), courseId);
    }

    @GetMapping("/courses/{courseId}/workspace")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy toàn bộ dữ liệu thực tế cho không gian chi tiết khóa học")
    public com.theieltsspells.studentportal.application.dto.StudentCourseWorkspaceResponse courseWorkspace(
            @PathVariable String courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return workspaceService.getWorkspace(studentId(jwt), courseId);
    }

    @GetMapping("/courses/{courseId}/portfolio")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy portfolio Speaking & Writing xuyên suốt khóa học")
    public StudentPortfolioResponse portfolio(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt) {
        return service.portfolio(studentId(jwt), courseId);
    }

    @GetMapping("/courses/{courseId}/progress")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    public List<StudentProgressTaskResponse> courseProgress(
            @PathVariable String courseId, @AuthenticationPrincipal Jwt jwt) {
        return progressService.list(studentId(jwt), courseId);
    }

    @PostMapping(value = "/courses/{courseId}/activities/{classActivityId}/evidence",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public ResponseEntity<Void> uploadProgressEvidence(
            @PathVariable String courseId,
            @PathVariable UUID classActivityId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        progressService.uploadEvidence(studentId(jwt), courseId, classActivityId, file);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/courses/{courseId}/progress/{attemptId}/checklist/{checklistItemId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public ResponseEntity<Void> updateProgressChecklist(
            @PathVariable String courseId,
            @PathVariable UUID attemptId,
            @PathVariable UUID checklistItemId,
            @RequestBody UpdateChecklistRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        progressService.updateChecklist(studentId(jwt), courseId, attemptId, checklistItemId, request.completed());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/courses/{courseId}/progress/{attemptId}/summary")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public ResponseEntity<Void> updateProgressSummary(
            @PathVariable String courseId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody UpdateProgressSummaryRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        progressService.updateSummary(studentId(jwt), courseId, attemptId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/courses/{courseId}/activities/{classActivityId}/submission")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public StudentSubmissionResponse saveSubmission(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @Valid @RequestBody StudentSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return assignmentService.saveStudentSubmission(studentId(jwt), courseId, classActivityId, request);
    }

    @GetMapping("/courses/{courseId}/activities/{classActivityId}/submissions/revisions")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    public List<StudentSubmissionRevisionResponse> submissionRevisions(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @AuthenticationPrincipal Jwt jwt) {
        return assignmentService.revisions(studentId(jwt), courseId, classActivityId);
    }

    @PostMapping(value = "/courses/{courseId}/activities/{classActivityId}/submission/audio",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public StudentSubmissionResponse saveAudioSubmission(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @RequestParam MultipartFile file,
            @RequestParam(name = "promptKey", defaultValue = "response-1") String promptKey,
            @RequestParam(name = "promptTitle", defaultValue = "Bài nói 1") String promptTitle,
            @RequestParam(name = "sortOrder", defaultValue = "0") int sortOrder,
            @RequestParam(name = "submit", defaultValue = "false") boolean submit,
            @AuthenticationPrincipal Jwt jwt) {
        return assignmentService.saveStudentAudioSubmission(
                studentId(jwt), courseId, classActivityId, file, promptKey, promptTitle, sortOrder, submit);
    }

    @GetMapping("/courses/{courseId}/activities/{classActivityId}/submissions/{submissionId}/audio")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    public ResponseEntity<InputStreamResource> audio(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal Jwt jwt) {
        var audio = assignmentService.openAudio(courseId, classActivityId, submissionId, studentId(jwt));
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

    @GetMapping("/courses/{courseId}/activities/{classActivityId}/submissions/{submissionId}/audio/{fileId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    public ResponseEntity<InputStreamResource> audioFile(
            @PathVariable UUID courseId,
            @PathVariable UUID classActivityId,
            @PathVariable UUID submissionId,
            @PathVariable UUID fileId,
            @AuthenticationPrincipal Jwt jwt) {
        var audio = assignmentService.openAudio(courseId, classActivityId, submissionId, fileId, studentId(jwt));
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

    @PatchMapping("/courses/{courseId}/progress/{attemptId}/reflection")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    public ResponseEntity<Void> saveReflection(
            @PathVariable UUID courseId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody ReflectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        assignmentService.saveReflection(studentId(jwt), courseId, attemptId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/target-band")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    @Operation(summary = "Học viên tự cập nhật Band mục tiêu")
    public StudentTargetBandResponse updateTargetBand(
            @Valid @RequestBody UpdateStudentTargetBandRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.updateTargetBand(
                studentId(jwt),
                request.targetBand(),
                request.listeningTarget(),
                request.readingTarget(),
                request.writingTarget(),
                request.speakingTarget()
        );
    }

    @PatchMapping("/exam-plan")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    @Operation(summary = "Học viên tự cập nhật kế hoạch thi IELTS")
    public StudentExamPlanResponse updateExamPlan(
            @Valid @RequestBody UpdateStudentExamPlanRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.updateExamPlan(studentId(jwt), request.noExamPlan(), request.plannedExamDate());
    }

    private UUID studentId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
