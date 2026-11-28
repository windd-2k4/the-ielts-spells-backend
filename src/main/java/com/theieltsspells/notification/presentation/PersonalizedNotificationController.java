package com.theieltsspells.notification.presentation;

import com.theieltsspells.notification.PersonalizedNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/mobile-notifications")
@RequiredArgsConstructor
@Tag(name = "Student - Personalized Notifications")
@SecurityRequirement(name = "bearerAuth")
public class PersonalizedNotificationController {

    private final PersonalizedNotificationService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách thông báo cá nhân hóa (nhắc học, streak, bạn bè, thách đấu, chấm bài)")
    public List<PersonalizedNotificationService.FeedNotice> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.getFeed(UUID.fromString(jwt.getSubject()), unreadOnly);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy số lượng thông báo chưa đọc")
    public Map<String, Integer> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unreadCount", service.unreadCount(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã đọc 1 thông báo")
    public void markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.markRead(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc")
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/study-schedule")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy lịch nhắc học bài cá nhân hóa")
    public PersonalizedNotificationService.StudyScheduleDto getSchedule(@AuthenticationPrincipal Jwt jwt) {
        return service.getSchedule(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/study-schedule")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật lịch nhắc học cá nhân hóa")
    public PersonalizedNotificationService.StudyScheduleDto saveSchedule(
            @RequestBody PersonalizedNotificationService.StudyScheduleDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.saveSchedule(UUID.fromString(jwt.getSubject()), request);
    }
}
