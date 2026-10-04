package com.theieltsspells.shared.security;

import com.theieltsspells.billing.application.AccountActivationService;
import com.theieltsspells.billing.application.dto.ActivateAccountResponse;
import com.theieltsspells.billing.application.dto.VerifyActivationTokenResponse;
import com.theieltsspells.billing.presentation.PublicActivationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicActivationController.class)
@Import(SecurityConfig.class)
class PublicActivationSecurityTests {

    @Autowired MockMvc mockMvc;
    @MockBean AccountActivationService activationService;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void activationLinkCanBeVerifiedWithoutSigningIn() throws Exception {
        when(activationService.verifyToken("token-123")).thenReturn(new VerifyActivationTokenResponse(
                true, "Nguyen Van A", "student@example.com", null, "IELTS Foundation", "Hợp lệ"));

        mockMvc.perform(get("/api/v1/auth/verify-activation-token").param("token", "token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void paidStudentCanSetPasswordWithoutSigningIn() throws Exception {
        when(activationService.activateAccount(any())).thenReturn(new ActivateAccountResponse(
                true, "Đã kích hoạt", null, "student@example.com", "Nguyen Van A", null, "/student/courses"));

        mockMvc.perform(post("/api/v1/auth/activate-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-123","password":"StrongPass123","confirmPassword":"StrongPass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void otherEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/not-a-public-route"))
                .andExpect(status().isUnauthorized());
    }
}
