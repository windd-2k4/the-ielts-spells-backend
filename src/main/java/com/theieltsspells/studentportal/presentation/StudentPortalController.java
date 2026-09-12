package com.theieltsspells.studentportal.presentation;

import com.theieltsspells.studentportal.application.StudentPortalService;
import com.theieltsspells.studentportal.application.dto.StudentPortalOverviewResponse;
import com.theieltsspells.studentportal.application.dto.StudentTargetBandResponse;
import com.theieltsspells.studentportal.application.dto.UpdateStudentTargetBandRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/portal")
@RequiredArgsConstructor
@Tag(name = "Student - Learning portal")
@SecurityRequirement(name = "bearerAuth")
public class StudentPortalController {

    private final StudentPortalService service;

    @GetMapping("/overview")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy dữ liệu tổng hợp thật cho góc học tập")
    public StudentPortalOverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return service.overview(studentId(jwt));
    }

    @PatchMapping("/target-band")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    @Operation(summary = "Học viên tự cập nhật Band mục tiêu")
    public StudentTargetBandResponse updateTargetBand(
            @Valid @RequestBody UpdateStudentTargetBandRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.updateTargetBand(studentId(jwt), request.targetBand());
    }

    private UUID studentId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
