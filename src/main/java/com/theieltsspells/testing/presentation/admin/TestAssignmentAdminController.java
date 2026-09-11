package com.theieltsspells.testing.presentation.admin;

import com.theieltsspells.testing.application.TestAssignmentApplicationService;
import com.theieltsspells.testing.application.dto.CreateTestAssignmentRequest;
import com.theieltsspells.testing.application.dto.TestAssignmentResponse;
import com.theieltsspells.shared.security.PermissionPolicy;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/test-assignments")
@RequiredArgsConstructor
@Tag(name = "Admin - Test assignments")
@SecurityRequirement(name = "bearerAuth")
public class TestAssignmentAdminController {

    private final TestAssignmentApplicationService service;
    private final PermissionPolicy permissionPolicy;

    @PostMapping
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #request.courseId(), 'test.assignment.manage')")
    public ResponseEntity<TestAssignmentResponse> create(@Valid @RequestBody CreateTestAssignmentRequest request,
                                                         @AuthenticationPrincipal Jwt jwt,
                                                         Authentication authentication) {
        var result = service.create(request, UUID.fromString(jwt.getSubject()), isModerator(authentication));
        return ResponseEntity.created(URI.create("/api/v1/admin/test-assignments/" + result.id())).body(result);
    }

    @GetMapping
    @PreAuthorize("@permissionPolicy.hasForCourse(authentication, #courseId, 'test.assignment.manage')")
    public List<TestAssignmentResponse> list(@RequestParam UUID courseId, @AuthenticationPrincipal Jwt jwt,
                                             Authentication authentication) {
        return service.list(courseId, UUID.fromString(jwt.getSubject()), isModerator(authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'test.assignment.manage')")
    public ResponseEntity<Void> archive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                        Authentication authentication) {
        service.archive(id, UUID.fromString(jwt.getSubject()), isModerator(authentication));
        return ResponseEntity.noContent().build();
    }

    private boolean isModerator(Authentication authentication) {
        return permissionPolicy.canManageTestAssignmentsAcrossCourses(authentication);
    }
}
