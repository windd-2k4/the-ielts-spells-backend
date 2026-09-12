package com.theieltsspells.reporting.presentation.admin;

import com.theieltsspells.reporting.application.AdminDashboardService;
import com.theieltsspells.reporting.application.dto.AdminDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin - Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final AdminDashboardService service;

    @GetMapping
    @PreAuthorize("@permissionPolicy.hasAdministrativeScope(authentication, 'course.read') "
            + "and @permissionPolicy.hasAdministrativeScope(authentication, 'enrollment.read')")
    @Operation(summary = "Lấy dữ liệu tổng hợp rút gọn cho dashboard quản trị")
    public AdminDashboardResponse overview() {
        return service.overview();
    }
}
