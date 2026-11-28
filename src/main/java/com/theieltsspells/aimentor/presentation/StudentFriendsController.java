package com.theieltsspells.aimentor.presentation;

import com.theieltsspells.aimentor.application.StudentFriendsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/friends")
@RequiredArgsConstructor
@Tag(name = "Student - Real Friends & 1v1 Challenges")
@SecurityRequirement(name = "bearerAuth")
public class StudentFriendsController {

    private final StudentFriendsService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách bạn bè thật & lời mời kết bạn đang chờ")
    public List<StudentFriendsService.FriendSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return service.getFriendsAndRequests(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tìm kiếm học viên thật theo tên / mã học viên / email")
    public List<StudentFriendsService.FriendSummary> search(
            @RequestParam(defaultValue = "") String q,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.searchLearners(UUID.fromString(jwt.getSubject()), q);
    }

    @PostMapping("/request")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi lời mời kết bạn")
    public ResponseEntity<?> sendRequest(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        service.sendFriendRequest(UUID.fromString(jwt.getSubject()), UUID.fromString(body.get("targetUserId")));
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chấp nhận lời mời kết bạn")
    public ResponseEntity<?> accept(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.acceptFriendRequest(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.ok(Map.of("accepted", true));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hủy kết bạn hoặc từ chối lời mời")
    public ResponseEntity<?> remove(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.removeFriendship(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/leaderboard")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Bảng xếp hạng PvP & Streak thật từ DB")
    public List<StudentFriendsService.FriendSummary> leaderboard() {
        return service.getRealLeaderboard();
    }

    @GetMapping("/challenges")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách lời mời thách đấu 1v1 đang mở")
    public List<StudentFriendsService.ChallengeDto> challenges(@AuthenticationPrincipal Jwt jwt) {
        return service.listMyChallenges(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/challenges")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gửi lời mời thách đấu 1v1 cho bạn bè")
    public ResponseEntity<?> createChallenge(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        try {
            UUID opponentId = UUID.fromString(body.get("opponentId").toString());
            String gameMode = body.get("gameMode") != null ? body.get("gameMode").toString() : "SPEED_VOCAB";
            String difficulty = body.get("difficulty") != null ? body.get("difficulty").toString() : "MEDIUM";
            int wager = body.get("wagerGems") instanceof Number n ? n.intValue() : 0;
            return ResponseEntity.ok(service.createChallenge(
                    UUID.fromString(jwt.getSubject()), opponentId, gameMode, difficulty, wager));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/challenges/{id}/respond")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chấp nhận hoặc từ chối lời mời thách đấu 1v1")
    public ResponseEntity<?> respondChallenge(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        try {
            boolean accept = Boolean.TRUE.equals(body.get("accept"));
            return ResponseEntity.ok(service.respondChallenge(UUID.fromString(jwt.getSubject()), id, accept));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
