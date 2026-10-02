package com.theieltsspells.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void preservesResponseStatusExceptionStatusAndReason() {
        var request = new MockHttpServletRequest("POST", "/api/v1/webhooks/sepay");

        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook không hợp lệ"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("HTTP_400");
        assertThat(response.getBody().message()).isEqualTo("Webhook không hợp lệ");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/webhooks/sepay");
    }
}
