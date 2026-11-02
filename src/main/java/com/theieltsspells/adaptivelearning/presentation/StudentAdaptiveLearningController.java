package com.theieltsspells.adaptivelearning.presentation;

import com.theieltsspells.adaptivelearning.application.AdaptiveLearningApplicationService;
import com.theieltsspells.adaptivelearning.application.dto.AdaptiveRecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/adaptive-learning")
@RequiredArgsConstructor
@Tag(name = "Student - Adaptive Learning")
@SecurityRequirement(name = "bearerAuth")
public class StudentAdaptiveLearningController {
    private final AdaptiveLearningApplicationService service;

    @GetMapping("/recommendation")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.read')")
    @Operation(summary = "Lấy hoạt động học tập thích ứng đang được ưu tiên")
    public ResponseEntity<AdaptiveRecommendationResponse> recommendation(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.of(service.currentRecommendation(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/recommendations/{recommendationId}/accept")
    @PreAuthorize("@permissionPolicy.has(authentication, 'identity.profile.self.update')")
    @Operation(summary = "Ghi nhận học viên bắt đầu hoạt động được đề xuất")
    public ResponseEntity<Void> accept(
            @PathVariable UUID recommendationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        service.acceptRecommendation(recommendationId, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}

