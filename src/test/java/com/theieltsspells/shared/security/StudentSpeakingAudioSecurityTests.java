package com.theieltsspells.shared.security;

import com.theieltsspells.testing.application.SpeakingAudioService;
import com.theieltsspells.testing.application.SpeakingExaminerSpeechService;
import com.theieltsspells.testing.application.SpeakingExamSessionService;
import com.theieltsspells.testing.application.SpeakingTurnService;
import com.theieltsspells.testing.application.SpeakingVocabService;
import com.theieltsspells.testing.application.StudentSpeakingDeliveryService;
import com.theieltsspells.testing.application.dto.SpeakingAudioUploadResponse;
import com.theieltsspells.testing.presentation.student.StudentSpeakingTestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentSpeakingTestController.class)
@Import({SecurityConfig.class, PermissionPolicy.class})
class StudentSpeakingAudioSecurityTests {
    @Autowired MockMvc mvc;
    @MockBean StudentSpeakingDeliveryService deliveryService;
    @MockBean SpeakingVocabService vocabService;
    @MockBean SpeakingAudioService audioService;
    @MockBean SpeakingExamSessionService examSessionService;
    @MockBean SpeakingTurnService turnService;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean CourseMembershipLookup memberships;

    @Test
    void anonymousCannotUploadSpeakingAudio() throws Exception {
        mvc.perform(uploadRequest(UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(audioService);
    }

    @Test
    void teacherCannotUploadSpeakingAudio() throws Exception {
        mvc.perform(uploadRequest(UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("teacher"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(audioService);
    }

    @Test
    void studentMayUploadSpeakingAudioForOwnedAttempt() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        String storageKey = "speaking-attempts/" + attemptId + "/audio.webm";
        when(audioService.upload(eq(attemptId), eq("part-1-q1"), any(), eq(studentId)))
                .thenReturn(new SpeakingAudioUploadResponse(storageKey, "/audio", 128, 1_000));

        mvc.perform(uploadRequest(attemptId)
                        .with(jwt().jwt(value -> value.subject(studentId.toString()))
                                .authorities(new SimpleGrantedAuthority("student"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageKey").value(storageKey));

        verify(audioService).upload(eq(attemptId), eq("part-1-q1"), any(), eq(studentId));
    }

    @Test
    void studentMayCreateOrResumeExaminerSession() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        mvc.perform(post("/api/v1/student/speaking/attempts/{attemptId}/exam-session", attemptId)
                        .with(jwt().jwt(value -> value.subject(studentId.toString()))
                                .authorities(new SimpleGrantedAuthority("student"))))
                .andExpect(status().isOk());

        verify(examSessionService).createOrResume(eq(attemptId), eq(studentId));
    }

    @Test
    void studentMayStreamExaminerPromptAudio() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(examSessionService.promptAudio(sessionId, turnId, studentId))
                .thenReturn(new SpeakingExaminerSpeechService.AudioContent(
                        new ByteArrayInputStream(new byte[]{1, 2, 3}), "audio/wav", 3));

        mvc.perform(get("/api/v1/student/speaking/exam-sessions/{sessionId}/turns/{turnId}/audio",
                        sessionId, turnId)
                        .with(jwt().jwt(value -> value.subject(studentId.toString()))
                                .authorities(new SimpleGrantedAuthority("student"))))
                .andExpect(status().isOk());

        verify(examSessionService).promptAudio(sessionId, turnId, studentId);
    }

    @Test
    void studentMayAdvancePartTwoPreparationWithTheExpectedVersion() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        mvc.perform(post("/api/v1/student/speaking/exam-sessions/{sessionId}/part-2/ready", sessionId)
                        .with(jwt().jwt(value -> value.subject(studentId.toString()))
                                .authorities(new SimpleGrantedAuthority("student")))
                        .contentType("application/json")
                        .content("{\"stateVersion\":4}"))
                .andExpect(status().isOk());

        verify(examSessionService).markPartTwoReady(eq(sessionId), any(), eq(studentId));
    }

    @Test
    void studentMayAbortExamSessionWithTheExpectedVersion() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        mvc.perform(post("/api/v1/student/speaking/exam-sessions/{sessionId}/abort", sessionId)
                        .with(jwt().jwt(value -> value.subject(studentId.toString()))
                                .authorities(new SimpleGrantedAuthority("student")))
                        .contentType("application/json")
                        .content("{\"stateVersion\":4}"))
                .andExpect(status().isOk());

        verify(examSessionService).abort(eq(sessionId), any(), eq(studentId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder uploadRequest(
            UUID attemptId) {
        return multipart("/api/v1/student/speaking/attempts/{attemptId}/audio", attemptId)
                .file(new MockMultipartFile("file", "ignored.webm", "audio/webm", new byte[]{1, 2, 3}))
                .param("questionKey", "part-1-q1");
    }
}
