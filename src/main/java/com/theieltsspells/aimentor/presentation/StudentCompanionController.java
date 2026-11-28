package com.theieltsspells.aimentor.presentation;

import com.theieltsspells.aimentor.application.StudentCompanionService;
import com.theieltsspells.aimentor.application.dto.StudentCompanionProfileDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/companion-profile")
@RequiredArgsConstructor
@Tag(name = "Student - AI Companion Profile")
@SecurityRequirement(name = "bearerAuth")
public class StudentCompanionController {

    private final StudentCompanionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy cấu hình linh vật đồng hành & lộ trình học tập theo tài khoản")
    public StudentCompanionProfileDto getProfile(@AuthenticationPrincipal Jwt jwt) {
        return service.getProfile(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lưu cấu hình linh vật Ignis/Spellie, giọng US/UK và mục tiêu Daily Routine")
    public StudentCompanionProfileDto upsertProfile(
            @Valid @RequestBody StudentCompanionProfileDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.upsertProfile(UUID.fromString(jwt.getSubject()), request);
    }
}
