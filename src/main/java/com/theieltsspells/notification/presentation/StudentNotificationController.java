package com.theieltsspells.notification.presentation;

import com.theieltsspells.notification.WritingNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/notifications/writing")
@RequiredArgsConstructor
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.result.read')")
public class StudentNotificationController {
    private final WritingNotificationService service;
    @GetMapping
    public List<WritingNotificationService.Notice> unread(@AuthenticationPrincipal Jwt jwt) {
        return service.unread(UUID.fromString(jwt.getSubject()));
    }
    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.markRead(id, UUID.fromString(jwt.getSubject()));
    }
}
