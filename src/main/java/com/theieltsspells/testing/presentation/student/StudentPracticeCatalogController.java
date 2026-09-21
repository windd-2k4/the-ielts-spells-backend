package com.theieltsspells.testing.presentation.student;

import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.web.PageResponse;
import com.theieltsspells.testing.application.StudentPracticeCatalogService;
import com.theieltsspells.testing.application.dto.StudentPracticeCatalogItemResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/practice-tests")
@RequiredArgsConstructor
@Tag(name = "Student - Practice catalog")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@permissionPolicy.has(authentication, 'assessment.attempt')")
public class StudentPracticeCatalogController {

    private final StudentPracticeCatalogService service;

    @GetMapping
    public PageResponse<StudentPracticeCatalogItemResponse> list(
            @RequestParam SkillType skill,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ALL") String testType,
            @RequestParam(defaultValue = "ALL") String format,
            @RequestParam(defaultValue = "") String questionTypes,
            @RequestParam(defaultValue = "ALL") String progress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.list(UUID.fromString(jwt.getSubject()), skill, query, testType, format,
                questionTypes, progress, page, size);
    }
}
