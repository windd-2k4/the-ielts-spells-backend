package com.theieltsspells.shared.security;

import com.theieltsspells.testing.application.SpeakingEvaluationAdminService;
import com.theieltsspells.testing.presentation.admin.SpeakingEvaluationAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpeakingEvaluationAdminController.class)
@Import({SecurityConfig.class, PermissionPolicy.class})
class SpeakingEvaluationSecurityTests {
    @Autowired MockMvc mvc;
    @MockBean SpeakingEvaluationAdminService service;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean CourseMembershipLookup memberships;

    @Test
    void studentCannotOpenSpeakingGradingQueue() throws Exception {
        mvc.perform(get("/api/v1/admin/speaking-evaluations")
                .with(jwt().authorities(new SimpleGrantedAuthority("student"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void teacherMayOpenSpeakingGradingQueue() throws Exception {
        mvc.perform(get("/api/v1/admin/speaking-evaluations")
                .with(jwt().authorities(new SimpleGrantedAuthority("teacher"))))
                .andExpect(status().isOk());
        verify(service).list();
    }
}
