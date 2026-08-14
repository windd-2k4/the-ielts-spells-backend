package com.theieltsspells.progress.presentation.admin;

import com.theieltsspells.progress.application.ClassProgressQueryService;
import com.theieltsspells.progress.application.dto.ClassActivityProgressResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/courses/{courseId}/progress")
@RequiredArgsConstructor
@Tag(name = "Admin - Learning progress")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('admin', 'manager', 'teacher')")
public class ClassProgressAdminController {
    private final ClassProgressQueryService service;
    @GetMapping public List<ClassActivityProgressResponse> list(@PathVariable UUID courseId) { return service.courseProgress(courseId); }
}
