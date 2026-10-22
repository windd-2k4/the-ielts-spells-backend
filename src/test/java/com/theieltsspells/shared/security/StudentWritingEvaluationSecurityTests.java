package com.theieltsspells.shared.security;

import com.theieltsspells.notification.WritingNotificationService;
import com.theieltsspells.notification.presentation.StudentNotificationController;
import com.theieltsspells.writingevaluation.application.WritingEvaluationApplicationService;
import com.theieltsspells.writingevaluation.presentation.student.StudentWritingEvaluationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({StudentWritingEvaluationController.class, StudentNotificationController.class})
@Import({SecurityConfig.class, PermissionPolicy.class})
class StudentWritingEvaluationSecurityTests {
    @Autowired MockMvc mvc;
    @MockBean WritingEvaluationApplicationService evaluations;
    @MockBean WritingNotificationService notifications;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean CourseMembershipLookup memberships;

    @Test
    void anonymousCannotRequestAiOrReadNotices() throws Exception {
        mvc.perform(post("/api/v1/student/writing/attempts/" + UUID.randomUUID() + "/ai-evaluation"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/notifications/writing")).andExpect(status().isUnauthorized());
        verifyNoInteractions(evaluations, notifications);
    }

    @Test
    void requestAndNotificationOwnershipAlwaysUseAuthenticatedStudent() throws Exception {
        UUID student = UUID.randomUUID(), attempt = UUID.randomUUID(), notice = UUID.randomUUID();
        var auth = jwt().jwt(value -> value.subject(student.toString()))
                .authorities(new SimpleGrantedAuthority("student"));
        mvc.perform(post("/api/v1/student/writing/attempts/" + attempt + "/ai-evaluation").with(auth))
                .andExpect(status().isOk());
        verify(evaluations).requestAi(attempt, student);
        mvc.perform(get("/api/v1/student/notifications/writing").with(auth)).andExpect(status().isOk());
        verify(notifications).unread(student);
        mvc.perform(post("/api/v1/student/notifications/writing/" + notice + "/read").with(auth))
                .andExpect(status().isOk());
        verify(notifications).markRead(notice, student);
    }
}
