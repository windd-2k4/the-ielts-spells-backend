package com.theieltsspells.testing.presentation.student;

import com.theieltsspells.testing.application.SpeakingAudioService;
import com.theieltsspells.testing.application.SpeakingExaminerSpeechService;
import com.theieltsspells.testing.application.SpeakingExamSessionService;
import com.theieltsspells.testing.application.SpeakingTurnService;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
    private final SpeakingAudioService audioService;
    private final SpeakingExamSessionService examSessionService;
    private final SpeakingTurnService turnService;

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

    @PostMapping(value = "/attempts/{attemptId}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpeakingAudioUploadResponse uploadAudio(
            @PathVariable UUID attemptId,
            @RequestParam String questionKey,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return audioService.upload(attemptId, questionKey, file, studentId(jwt));
    }

    @GetMapping("/attempts/{attemptId}/audio/{questionKey}")
    public ResponseEntity<InputStreamResource> audio(
            @PathVariable UUID attemptId,
            @PathVariable String questionKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return audioResponse(audioService.openOwned(attemptId, questionKey, studentId(jwt)));
    }

    @PostMapping("/attempts/{attemptId}/exam-session")
    public SpeakingExaminerSessionResponse createOrResumeExaminerSession(
            @PathVariable UUID attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return examSessionService.createOrResume(attemptId, studentId(jwt));
    }

    @GetMapping("/exam-sessions/{sessionId}")
    public SpeakingExaminerSessionResponse examinerSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return examSessionService.get(sessionId, studentId(jwt));
    }

    @PostMapping("/exam-sessions/{sessionId}/turns")
    public SpeakingExaminerTurnSubmissionResponse submitExaminerTurn(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitSpeakingExaminerTurnRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return turnService.submit(sessionId, request, studentId(jwt));
    }

    @PostMapping("/exam-sessions/{sessionId}/part-2/ready")
    public SpeakingExaminerSessionResponse partTwoReady(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SpeakingExamPartTwoReadyRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return examSessionService.markPartTwoReady(sessionId, request, studentId(jwt));
    }

    @PostMapping("/exam-sessions/{sessionId}/abort")
    public SpeakingExaminerSessionResponse abortExamSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AbortSpeakingExamSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return examSessionService.abort(sessionId, request, studentId(jwt));
    }

    @GetMapping("/exam-sessions/{sessionId}/turns/{turnId}/audio")
    public ResponseEntity<InputStreamResource> examinerPromptAudio(
            @PathVariable UUID sessionId,
            @PathVariable UUID turnId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return examinerAudioResponse(examSessionService.promptAudio(sessionId, turnId, studentId(jwt)));
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

    private ResponseEntity<InputStreamResource> audioResponse(SpeakingAudioService.AudioContent audio) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.mimeType()))
                .contentLength(audio.sizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("speaking-response.webm", StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(audio.inputStream()));
    }

    private ResponseEntity<InputStreamResource> examinerAudioResponse(SpeakingExaminerSpeechService.AudioContent audio) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.mimeType()))
                .contentLength(audio.sizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("examiner-prompt.wav", StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(audio.inputStream()));
    }
}
