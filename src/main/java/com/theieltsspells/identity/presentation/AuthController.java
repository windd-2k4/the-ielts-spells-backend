package com.theieltsspells.identity.presentation;

import com.theieltsspells.identity.application.dto.CurrentUserResponse;
import com.theieltsspells.identity.application.StudentOnboardingService;
import com.theieltsspells.identity.application.dto.StudentOnboardingRequest;
import com.theieltsspells.identity.application.dto.StudentOnboardingResponse;
import com.theieltsspells.shared.application.ConflictException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
@SecurityRequirement(name = "bearerAuth")
public class AuthController {
    private final StudentOnboardingService studentOnboarding;

    @GetMapping("/me")
    @Operation(summary = "Thông tin người dùng từ Supabase access token")
    public CurrentUserResponse me(Authentication authentication) {
        var jwt = (Jwt) authentication.getPrincipal();
        var roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .toList();
        return new CurrentUserResponse(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), roles);
    }

    @PostMapping("/student/onboarding")
    @Operation(summary = "Hoàn tất hồ sơ và quyền cho tài khoản học viên tự đăng ký")
    public StudentOnboardingResponse onboardStudent(Authentication authentication,
                                                       @Valid @RequestBody StudentOnboardingRequest request) {
        var staffRole = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(role -> !role.equals("student"))
                .findFirst();
        if (staffRole.isPresent()) {
            throw new ConflictException("Tài khoản nhân sự không thể tự đăng ký thành học viên");
        }

        var jwt = (Jwt) authentication.getPrincipal();
        return studentOnboarding.onboard(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                request.fullName());
    }
}
