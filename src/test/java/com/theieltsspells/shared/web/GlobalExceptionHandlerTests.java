package com.theieltsspells.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsForbiddenForMethodAuthorizationDenial() {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/courses");

        var response = handler.handleAccessDenied(
                new AuthorizationDeniedException("Access Denied"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/admin/courses");
    }
}
