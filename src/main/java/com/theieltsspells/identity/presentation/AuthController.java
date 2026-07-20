package com.theieltsspells.identity.presentation;

import com.theieltsspells.identity.application.dto.CurrentUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
@SecurityRequirement(name = "bearerAuth")
public class AuthController {

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
}
