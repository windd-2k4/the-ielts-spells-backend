package com.theieltsspells.writingevaluation.presentation.admin;

import com.theieltsspells.writingevaluation.application.WritingEvaluationApplicationService;
import com.theieltsspells.writingevaluation.application.dto.ReviewWritingEvaluationRequest;
import com.theieltsspells.writingevaluation.application.dto.UpdateWritingAiEntitlementRequest;
import com.theieltsspells.writingevaluation.application.dto.WritingAiEntitlementResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingEvaluationFilterOptionsResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingEvaluationResponse;
import com.theieltsspells.writingevaluation.application.dto.WritingTeacherDraftRequest;
import com.theieltsspells.writingevaluation.application.dto.WritingCalibrationResponse;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.shared.security.PermissionPolicy;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/writing-evaluations")
@RequiredArgsConstructor
@Tag(name = "Admin - Writing evaluation")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.grade')")
public class WritingEvaluationAdminController {
    private final WritingEvaluationApplicationService service;
    private final PermissionPolicy permissionPolicy;

    @GetMapping
    public PageResponse<WritingEvaluationResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "ALL") String origin,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String teacherStatus,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return service.list(status, origin, courseId, teacherStatus, q, page, size,
                UUID.fromString(jwt.getSubject()), permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @GetMapping("/filter-options")
    public WritingEvaluationFilterOptionsResponse filterOptions(@AuthenticationPrincipal Jwt jwt,
                                                                Authentication authentication) {
        return service.filterOptions(UUID.fromString(jwt.getSubject()),
                permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @GetMapping("/calibration")
    public WritingCalibrationResponse calibration() { return service.calibration(); }

    @GetMapping("/{id}")
    public WritingEvaluationResponse get(@PathVariable UUID id,
                                         @AuthenticationPrincipal Jwt jwt,
                                         Authentication authentication) {
        return service.get(id, UUID.fromString(jwt.getSubject()), permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @PostMapping("/{id}/retry")
    public WritingEvaluationResponse retry(@PathVariable UUID id,
                                           @AuthenticationPrincipal Jwt jwt,
                                           Authentication authentication) {
        return service.retry(id, UUID.fromString(jwt.getSubject()), permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @PutMapping("/{id}/review")
    public WritingEvaluationResponse review(@PathVariable UUID id,
                                            @Valid @RequestBody ReviewWritingEvaluationRequest request,
                                             @AuthenticationPrincipal Jwt jwt,
                                             Authentication authentication) {
        return service.review(id, request, UUID.fromString(jwt.getSubject()),
                permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @PutMapping("/{id}/draft")
    public WritingEvaluationResponse draft(@PathVariable UUID id,
                                           @Valid @RequestBody WritingTeacherDraftRequest request,
                                           @AuthenticationPrincipal Jwt jwt,
                                           Authentication authentication) {
        return service.saveDraft(id, request, UUID.fromString(jwt.getSubject()),
                permissionPolicy.canGradeAcrossCourses(authentication));
    }

    @GetMapping("/entitlements")
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.update_academic')")
    public List<WritingAiEntitlementResponse> entitlements(@RequestParam(defaultValue = "") String q) {
        return service.listEntitlements(q);
    }

    @PutMapping("/entitlements/{studentId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.update_academic')")
    public WritingAiEntitlementResponse updateEntitlement(
            @PathVariable UUID studentId,
            @Valid @RequestBody UpdateWritingAiEntitlementRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.updateEntitlement(studentId, request, UUID.fromString(jwt.getSubject()));
    }
}
