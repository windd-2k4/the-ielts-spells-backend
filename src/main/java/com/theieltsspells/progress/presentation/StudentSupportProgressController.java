package com.theieltsspells.progress.presentation;

import com.theieltsspells.progress.application.ClassProgressQueryService;
import com.theieltsspells.progress.application.dto.ClassActivityProgressResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Student Support progress view, owned by the progress module. */
@RestController
@RequestMapping("/api/v1/student-support/courses/{courseId}/progress")
@RequiredArgsConstructor
@Tag(name = "Student Support - Assigned course progress")
@SecurityRequirement(name = "bearerAuth")
public class StudentSupportProgressController {

    private final ClassProgressQueryService progress;

    @GetMapping
    @PreAuthorize("@permissionPolicy.isStudentSupportAssignedToCourse(authentication, #courseId)")
    public List<ClassActivityProgressResponse> progress(@PathVariable UUID courseId) {
        return progress.courseProgress(courseId);
    }
}
