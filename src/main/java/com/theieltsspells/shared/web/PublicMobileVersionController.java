package com.theieltsspells.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public/mobile")
@Tag(name = "Public - Mobile App Version")
public class PublicMobileVersionController {

    @GetMapping("/version-check")
    @Operation(summary = "Kiểm tra phiên bản & trạng thái bảo trì cho ứng dụng Mobile/Web Companion")
    public Map<String, Object> versionCheck(
            @RequestParam(defaultValue = "WEB") String platform,
            @RequestParam(defaultValue = "1.0.0") String currentVersion,
            @RequestParam(defaultValue = "1") String buildNumber
    ) {
        return Map.of(
                "action", "UP_TO_DATE",
                "platform", platform,
                "currentVersion", currentVersion,
                "latestVersion", "1.0.0",
                "minSupportedVersion", "1.0.0",
                "storeUrl", "https://theieltsspells.com/app",
                "isUnderMaintenance", false,
                "maintenanceTitle", "Hệ thống đang bảo trì",
                "maintenanceMessage", "The IELTS Spells đang nâng cấp hạ tầng. Vui lòng quay lại sau.",
                "releaseNotes", "• Đồng bộ Ignis & Spellie AI Companion\n• Lộ trình học tập 3 chặng hằng ngày"
        );
    }
}
