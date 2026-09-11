package com.theieltsspells.academic.presentation;

import com.theieltsspells.academic.application.CourseStudentSupportApplicationService;
import com.theieltsspells.academic.application.dto.CourseResponse;
import com.theieltsspells.academic.application.dto.StudentSupportStudentResponse;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Read-only learner-support workspace, always constrained to assigned courses. */
@RestController
@RequestMapping("/api/v1/student-support")
@RequiredArgsConstructor
@Tag(name = "Student Support - Assigned courses")
@SecurityRequirement(name = "bearerAuth")
public class StudentSupportCourseController {

    private final CourseStudentSupportApplicationService courseSupport;

    @GetMapping("/courses")
    @PreAuthorize("@permissionPolicy.isStudentSupport(authentication)")
    public List<CourseResponse> courses(@AuthenticationPrincipal Jwt jwt) {
        return courseSupport.assignedCourses(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/courses/{courseId}/students")
    @PreAuthorize("@permissionPolicy.isStudentSupportAssignedToCourse(authentication, #courseId)")
    public PageResponse<StudentSupportStudentResponse> students(
            @PathVariable UUID courseId,
            @PageableDefault(size = 30, sort = "enrolledAt") Pageable pageable
    ) {
        return PageResponse.from(courseSupport.students(courseId, pageable));
    }

}
