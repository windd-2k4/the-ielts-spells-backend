package com.theieltsspells.shared.security;

import com.theieltsspells.writingevaluation.application.WritingEvaluationApplicationService;
import com.theieltsspells.writingevaluation.presentation.admin.WritingEvaluationAdminController;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(WritingEvaluationAdminController.class)
@Import({SecurityConfig.class, PermissionPolicy.class})
class WritingEvaluationSecurityTests {
    @Autowired MockMvc mvc;
    @MockBean WritingEvaluationApplicationService service;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean CourseMembershipLookup memberships;

    @Test
    void anonymousAndStudentsCannotAccessGradingQueueOrRetry() throws Exception {
        mvc.perform(get("/api/v1/admin/writing-evaluations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/writing-evaluations/" + UUID.randomUUID() + "/retry")
                .with(jwt().authorities(new SimpleGrantedAuthority("student")))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/writing-evaluations/" + UUID.randomUUID() + "/draft")
                .contentType(APPLICATION_JSON).content("{\"editedText\":\"draft\",\"annotations\":[]}")
                .with(jwt().authorities(new SimpleGrantedAuthority("student")))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void teacherMayRetryThroughExistingAssessmentPermission() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/writing-evaluations/" + id + "/retry")
                .with(jwt().authorities(new SimpleGrantedAuthority("teacher")))).andExpect(status().isOk());
        verify(service).retry(id);
    }

    @Test
    void onlyAcademicManagersMayChangeStudentAiEntitlement() throws Exception {
        UUID studentId = UUID.randomUUID();
        String body = "{\"mode\":\"UNLIMITED\"}";

        mvc.perform(put("/api/v1/admin/writing-evaluations/entitlements/" + studentId)
                .contentType(APPLICATION_JSON).content(body)
                .with(jwt().jwt(value -> value.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("teacher"))))
                .andExpect(status().isForbidden());

        UUID adminId = UUID.randomUUID();
        mvc.perform(put("/api/v1/admin/writing-evaluations/entitlements/" + studentId)
                .contentType(APPLICATION_JSON).content(body)
                .with(jwt().jwt(value -> value.subject(adminId.toString()))
                        .authorities(new SimpleGrantedAuthority("admin"))))
                .andExpect(status().isOk());

        verify(service).updateEntitlement(eq(studentId), any(), eq(adminId));
    }
}
