package com.theieltsspells.studentportal.presentation;

import com.theieltsspells.studentportal.application.StudentPortalService;
import com.theieltsspells.studentportal.application.dto.StudentCourseResponse;
import com.theieltsspells.studentportal.application.dto.StudentCourseSessionResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/portal")
@RequiredArgsConstructor
@Tag(name = "Student - Learning portal")
@SecurityRequirement(name = "bearerAuth")
public class StudentPortalController {

    private final StudentPortalService service;
    private final com.theieltsspells.studentportal.application.StudentCourseWorkspaceService workspaceService;

    @GetMapping("/overview")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy dữ liệu tổng hợp thật cho góc học tập")
    public StudentPortalOverviewResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return service.overview(studentId(jwt));
    }

    @GetMapping("/courses")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy danh sách khóa học hiển thị cho học viên")
    public List<StudentCourseResponse> courses(@AuthenticationPrincipal Jwt jwt) {
        return service.courses(studentId(jwt));
    }

    @GetMapping("/courses/{courseId}/sessions")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy lịch học của khóa học mà học viên đã ghi danh")
    public List<StudentCourseSessionResponse> courseSessions(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.courseSessions(studentId(jwt), courseId);
    }

    @GetMapping("/courses/{courseId}/workspace")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy toàn bộ dữ liệu thực tế cho không gian chi tiết khóa học")
    public com.theieltsspells.studentportal.application.dto.StudentCourseWorkspaceResponse courseWorkspace(
            @PathVariable String courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return workspaceService.getWorkspace(studentId(jwt), courseId);
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
