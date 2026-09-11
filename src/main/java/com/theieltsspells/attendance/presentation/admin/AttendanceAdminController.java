package com.theieltsspells.attendance.presentation.admin;

import com.theieltsspells.attendance.application.AttendanceApplicationService;
import com.theieltsspells.attendance.application.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/courses/{courseId}/attendance")
@RequiredArgsConstructor
@Tag(name = "Admin - Attendance")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceAdminController {
    private final AttendanceApplicationService service;

    @GetMapping("/sessions")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'attendance.read')")
    @Operation(summary = "Danh sách tình trạng điểm danh theo từng buổi")
    public List<AttendanceSessionSummaryResponse> sessions(@PathVariable UUID courseId) {
        return service.listSessions(courseId);
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'attendance.read')")
    @Operation(summary = "Chi tiết phiếu điểm danh của một buổi")
    public AttendanceSheetResponse sheet(@PathVariable UUID courseId, @PathVariable UUID sessionId) {
        return service.getSheet(courseId, sessionId);
    }

    @PostMapping("/sessions/{sessionId}/initialize")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'attendance.mark')")
    @Operation(summary = "Khởi tạo phiếu từ danh sách học viên hợp lệ tại ngày học")
    public AttendanceSheetResponse initialize(@PathVariable UUID courseId, @PathVariable UUID sessionId,
                                              @AuthenticationPrincipal Jwt jwt) {
        return service.initialize(courseId, sessionId, actor(jwt));
    }

    @PutMapping("/sessions/{sessionId}/draft")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'attendance.mark')")
    @Operation(summary = "Lưu nháp nhiều dòng điểm danh")
    public AttendanceSheetResponse saveDraft(@PathVariable UUID courseId, @PathVariable UUID sessionId,
                                             @Valid @RequestBody BulkUpdateAttendanceRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return service.saveDraft(courseId, sessionId, request, actor(jwt));
    }

    @PostMapping("/sessions/{sessionId}/lock")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'attendance.mark')")
    @Operation(summary = "Xác nhận và khóa phiếu; không cho phép còn học viên chưa đánh dấu")
    public AttendanceSheetResponse lock(@PathVariable UUID courseId, @PathVariable UUID sessionId,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.lock(courseId, sessionId, actor(jwt));
    }

    @PostMapping("/sessions/{sessionId}/reopen")
    @PreAuthorize("@permissionPolicy.has(authentication, 'attendance.reopen')")
    @Operation(summary = "Mở lại phiếu đã khóa; bắt buộc có lý do")
    public AttendanceSheetResponse reopen(@PathVariable UUID courseId, @PathVariable UUID sessionId,
                                          @AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody ReopenAttendanceRequest request) {
        return service.reopen(courseId, sessionId, actor(jwt), request.reason());
    }

    @GetMapping
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'attendance.read')")
    @Operation(summary = "API tương thích: tất cả bản ghi điểm danh của khóa")
    public List<AttendanceResponse> list(@PathVariable UUID courseId) {
        return service.list(courseId);
    }

    @PutMapping
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'attendance.mark')")
    @Operation(summary = "API tương thích: cập nhật một dòng trong phiếu đang mở")
    public AttendanceResponse upsert(@PathVariable UUID courseId,
                                     @Valid @RequestBody UpsertAttendanceRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.upsert(courseId, request, actor(jwt));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
