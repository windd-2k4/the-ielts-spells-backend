package com.theieltsspells.aimentor.presentation;

import com.theieltsspells.aimentor.application.StudentEconomyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/economy")
@RequiredArgsConstructor
@Tag(name = "Student - Gems & Spells Shop")
@SecurityRequirement(name = "bearerAuth")
public class StudentEconomyController {

    private final StudentEconomyService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy ví Kim cương, trang bị đang mặc & danh sách vật phẩm Cửa hàng")
    public StudentEconomyService.WalletResponse getWallet(@AuthenticationPrincipal Jwt jwt) {
        return service.getWallet(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/claim-routine")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Nhận thưởng Kim cương cho chặng Lộ trình ngày hoặc Rương ngày")
    public Map<String, Object> claimRoutine(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.claimRoutineReward(UUID.fromString(jwt.getSubject()), body.get("stepKey"));
    }

    @PostMapping("/shop/buy")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mua Khiên Đóng Băng, Boost hoặc Trang bị linh vật bằng Kim cương")
    public ResponseEntity<?> buyItem(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        try {
            return ResponseEntity.ok(service.buyItem(UUID.fromString(jwt.getSubject()), body.get("itemId")));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/shop/equip")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trang bị hoặc tháo phụ kiện / danh hiệu đã mua")
    public ResponseEntity<?> equipItem(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        try {
            return ResponseEntity.ok(service.equipItem(UUID.fromString(jwt.getSubject()), body.get("itemId")));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/pvp-result")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ghi nhận kết quả trận PvP (giao hữu hoặc xếp hạng) và nhận Kim cương thưởng")
    public StudentEconomyService.WalletResponse recordPvpResult(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean isVictory = Boolean.TRUE.equals(body.get("isVictory"));
        boolean isRankedMatch = Boolean.TRUE.equals(body.get("isRankedMatch"));
        return service.recordPvpMatch(UUID.fromString(jwt.getSubject()), isVictory, isRankedMatch);
    }
}
