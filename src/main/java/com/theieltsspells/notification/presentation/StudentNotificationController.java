package com.theieltsspells.notification.presentation;

import com.theieltsspells.notification.WritingNotificationService;
import com.theieltsspells.notification.application.dto.StudentNotificationPreferencesResponse;
import com.theieltsspells.notification.application.dto.UpdateStudentNotificationPreferencesRequest;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/student/notifications", "/api/v1/student/notifications/writing"})
@RequiredArgsConstructor
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.result.read')")
public class StudentNotificationController {
    private final WritingNotificationService service;

    @GetMapping
    public List<WritingNotificationService.Notice> list(
            @RequestParam(required = false) Boolean unreadOnly,
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        if (Boolean.TRUE.equals(unreadOnly)) {
            return service.unread(studentId);
        }
        boolean isLegacyWritingPath = request.getRequestURI().endsWith("/writing");
        if (isLegacyWritingPath && unreadOnly == null) {
            return service.unread(studentId);
        }
        return service.list(studentId, false);
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.markRead(id, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/preferences")
    public StudentNotificationPreferencesResponse preferences(@AuthenticationPrincipal Jwt jwt) {
        return service.preferences(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/preferences")
    public StudentNotificationPreferencesResponse updatePreferences(
            @Valid @RequestBody UpdateStudentNotificationPreferencesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updatePreferences(UUID.fromString(jwt.getSubject()), request);
    }
}
