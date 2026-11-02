package com.theieltsspells.adaptivelearning.presentation.admin;

import com.theieltsspells.adaptivelearning.application.AdaptiveLearningApplicationService;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveLearnerProfileResponse;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveContentMappingsResponse;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveKnowledgeGraphResponse;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveLearningOverviewResponse;
import com.theieltsspells.adaptivelearning.application.dto.UpsertAdaptiveContentMappingRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/adaptive-learning")
@RequiredArgsConstructor
@Tag(name = "Admin - Adaptive Learning")
@SecurityRequirement(name = "bearerAuth")
public class AdaptiveLearningAdminController {
    private final AdaptiveLearningApplicationService service;

    @GetMapping
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.read')")
    @Operation(summary = "Theo dõi trạng thái và độ phủ của bounded module Adaptive Learning")
    public AdaptiveLearningOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/knowledge-map")
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.read')")
    @Operation(summary = "Xem cấu trúc kiến thức và độ phủ mapping của Adaptive Learning")
    public AdaptiveKnowledgeGraphResponse knowledgeMap() {
        return service.knowledgeGraph();
    }

    @GetMapping("/content-mappings")
    @PreAuthorize("@permissionPolicy.has(authentication, 'adaptive_learning.manage')")
    @Operation(summary = "Liệt kê học liệu có thể được Adaptive Learning đề xuất")
    public AdaptiveContentMappingsResponse contentMappings() {
        return service.contentMappings();
    }

    @PostMapping("/content-mappings")
    @PreAuthorize("@permissionPolicy.has(authentication, 'adaptive_learning.manage')")
    @Operation(summary = "Tạo content mapping cho Adaptive Learning")
    public AdaptiveContentMappingsResponse.Mapping createContentMapping(
            @Valid @RequestBody UpsertAdaptiveContentMappingRequest request
    ) {
        return service.createContentMapping(request);
    }

    @PutMapping("/content-mappings/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'adaptive_learning.manage')")
    @Operation(summary = "Cập nhật content mapping của Adaptive Learning")
    public AdaptiveContentMappingsResponse.Mapping updateContentMapping(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertAdaptiveContentMappingRequest request
    ) {
        return service.updateContentMapping(id, request);
    }

    @GetMapping("/learners/{learnerId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'student.profile.read')")
    @Operation(summary = "Xem trạng thái năng lực và lịch sử đề xuất của một học viên")
    public AdaptiveLearnerProfileResponse learner(@PathVariable UUID learnerId) {
        return service.learnerProfile(learnerId);
    }
}

