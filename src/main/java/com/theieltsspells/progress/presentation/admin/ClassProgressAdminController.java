package com.theieltsspells.progress.presentation.admin;

import com.theieltsspells.progress.application.ClassProgressQueryService;
import com.theieltsspells.progress.application.dto.ClassActivityProgressResponse;
import com.theieltsspells.progress.application.dto.CreateCourseActivityRequest;
import com.theieltsspells.shared.persistence.enums.SkillType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/courses/{courseId}/progress")
@RequiredArgsConstructor
@Tag(name = "Admin - Learning progress")
@SecurityRequirement(name = "bearerAuth")
public class ClassProgressAdminController {
    private final ClassProgressQueryService service;
    @GetMapping
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'progress.read')")
    public List<ClassActivityProgressResponse> list(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) SkillType skill) {
        return service.courseProgress(courseId, sessionId, skill);
    }

    @PostMapping("/activities")
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'assessment.grade')")
    public Map<String, UUID> createActivity(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateCourseActivityRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return Map.of("id", service.createActivity(courseId, request, UUID.fromString(jwt.getSubject())));
    }
}
