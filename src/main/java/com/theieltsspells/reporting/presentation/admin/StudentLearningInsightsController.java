package com.theieltsspells.reporting.presentation.admin;

import com.theieltsspells.reporting.application.StudentLearningInsightsService;
import com.theieltsspells.reporting.application.dto.StudentLearningInsightsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/students/{studentId}/learning-insights")
@RequiredArgsConstructor
@Tag(name = "Admin - Student learning insights")
@SecurityRequirement(name = "bearerAuth")
public class StudentLearningInsightsController {

    private final StudentLearningInsightsService service;

    @GetMapping
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.read')")
    @Operation(summary = "Tổng hợp lịch sử làm bài, năng lực và lỗi lặp lại của học viên")
    public StudentLearningInsightsResponse get(@PathVariable UUID studentId) {
        return service.get(studentId);
    }
}
