package com.theieltsspells.billing.presentation;

import com.theieltsspells.billing.application.AccountActivationService;
import com.theieltsspells.billing.application.dto.ActivateAccountRequest;
import com.theieltsspells.billing.application.dto.ActivateAccountResponse;
import com.theieltsspells.billing.application.dto.VerifyActivationTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication & Activation")
public class PublicActivationController {

    private final AccountActivationService activationService;

    @GetMapping("/verify-activation-token")
    @Operation(summary = "Kiểm tra tính hợp lệ của liên kết kích hoạt tài khoản")
    public ResponseEntity<VerifyActivationTokenResponse> verifyToken(@RequestParam String token) {
        return ResponseEntity.ok(activationService.verifyToken(token));
    }

    @PostMapping("/activate-account")
    @Operation(summary = "Học viên tạo mật khẩu và kích hoạt tài khoản đã mua khóa học")
    public ResponseEntity<ActivateAccountResponse> activateAccount(@Valid @RequestBody ActivateAccountRequest request) {
        return ResponseEntity.ok(activationService.activateAccount(request));
    }
}
