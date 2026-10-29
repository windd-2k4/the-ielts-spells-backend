package com.theieltsspells.reporting.presentation.admin;

import com.theieltsspells.reporting.application.TeacherDashboardApplicationService;
import com.theieltsspells.reporting.application.dto.TeacherDashboardResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/teacher-dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin - Teacher dashboard")
@SecurityRequirement(name = "bearerAuth")
public class TeacherDashboardController {

    private final TeacherDashboardApplicationService service;

    @GetMapping
    @PreAuthorize("@permissionPolicy.isTeacher(authentication) && @permissionPolicy.has(authentication, 'progress.read')")
    public TeacherDashboardResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return service.overview(UUID.fromString(jwt.getSubject()));
    }
}
