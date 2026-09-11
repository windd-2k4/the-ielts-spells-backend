package com.theieltsspells.learninglibrary.presentation.admin;

import com.theieltsspells.learninglibrary.application.LearningLibraryApplicationService;
import com.theieltsspells.learninglibrary.application.dto.*;
import com.theieltsspells.shared.persistence.enums.SkillType;
import com.theieltsspells.shared.security.PermissionPolicy;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/library")
@RequiredArgsConstructor
@Tag(name = "Admin - Learning library")
@SecurityRequirement(name = "bearerAuth")
public class LearningLibraryAdminController {
    private final LearningLibraryApplicationService service;
    private final PermissionPolicy permissionPolicy;

    @GetMapping("/summary")
    @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)")
    public ContentHubSummaryResponse summary() { return service.summary(); }

    @GetMapping("/resources")
    @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)")
    public PageResponse<LearningResourceResponse> resources(
            @RequestParam(required = false) String query, @RequestParam(required = false) SkillType skill,
            @RequestParam(required = false) String category, @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID courseId,
            @RequestParam(defaultValue = "false") boolean includeGlobal,
            @PageableDefault(size = 24, sort = "updatedAt") Pageable pageable) {
        return PageResponse.from(service.listResources(query, skill, category, scope, status, courseId, includeGlobal, pageable));
    }

    @GetMapping("/resources/{id}") @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)") public LearningResourceResponse resource(@PathVariable UUID id) { return service.getResource(id); }
    @GetMapping("/resources/{id}/files") @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)") public List<LearningResourceFileResponse> resourceFiles(@PathVariable UUID id) { return service.listResourceFiles(id); }
    @PostMapping("/resources") @PreAuthorize("@permissionPolicy.has(authentication, 'library.draft.create')") public ResponseEntity<LearningResourceResponse> createResource(
            @Valid @RequestBody LearningResourceRequest request, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        var result = service.createResource(request, actor(jwt), canManageAny(authentication));
        return ResponseEntity.created(URI.create("/api/v1/admin/library/resources/" + result.id())).body(result);
    }
    @PutMapping("/resources/{id}") @PreAuthorize("@permissionPolicy.has(authentication, 'library.draft.update_own')") public LearningResourceResponse updateResource(@PathVariable UUID id, @Valid @RequestBody LearningResourceRequest request, @AuthenticationPrincipal Jwt jwt, Authentication authentication) { return service.updateResource(id, request, actor(jwt), canManageAny(authentication)); }
    @DeleteMapping("/resources/{id}") @PreAuthorize("@permissionPolicy.has(authentication, 'library.draft.update_own')") public ResponseEntity<Void> archiveResource(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) { service.archiveResource(id, actor(jwt), canManageAny(authentication)); return ResponseEntity.noContent().build(); }
    @PostMapping(value = "/resources/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionPolicy.has(authentication, 'academic_media.manage_own')")
    public ResponseEntity<LearningResourceFileResponse> uploadResourceFile(@PathVariable UUID id,
            @RequestParam MultipartFile file, @RequestParam(defaultValue = "MAIN") String fileRole,
            @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        var result = service.uploadResourceFile(id, fileRole, file, actor(jwt), canManageAny(authentication));
        return ResponseEntity.created(URI.create("/api/v1/admin/library/resources/" + id + "/files/" + result.id())).body(result);
    }
    @DeleteMapping("/resources/{resourceId}/files/{fileId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'academic_media.manage_own')")
    public ResponseEntity<Void> deleteResourceFile(@PathVariable UUID resourceId, @PathVariable UUID fileId,
                                                    @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        service.deleteResourceFile(resourceId, fileId, actor(jwt), canManageAny(authentication)); return ResponseEntity.noContent().build();
    }
    @GetMapping("/files/{fileId}/content")
    @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)")
    public ResponseEntity<InputStreamResource> resourceFileContent(@PathVariable UUID fileId) {
        var content = service.openResourceFile(fileId);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(content.mimeType()); }
        catch (IllegalArgumentException ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content.inputStream()));
    }

    @GetMapping("/media")
    @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)")
    public PageResponse<MediaAssetResponse> media(@RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 24, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(service.listMedia(query, type, pageable));
    }
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionPolicy.has(authentication, 'academic_media.manage_own')")
    public ResponseEntity<MediaAssetResponse> uploadMedia(@RequestParam MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        var result = service.uploadMedia(file, actor(jwt));
        return ResponseEntity.created(URI.create("/api/v1/admin/library/media/" + result.id())).body(result);
    }
    @DeleteMapping("/media/{fileId}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'academic_media.manage_own')")
    public ResponseEntity<Void> deleteMedia(@PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt,
                                            Authentication authentication) {
        service.deleteMedia(fileId, actor(jwt), canManageAny(authentication)); return ResponseEntity.noContent().build();
    }

    @GetMapping("/exercises")
    @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)")
    public PageResponse<ExerciseTemplateResponse> exercises(
            @RequestParam(required = false) String query, @RequestParam(required = false) SkillType skill,
            @RequestParam(required = false) String category, @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID courseId,
            @RequestParam(defaultValue = "false") boolean includeGlobal,
            @PageableDefault(size = 24, sort = "updatedAt") Pageable pageable) {
        return PageResponse.from(service.listExercises(query, skill, category, scope, status, courseId, includeGlobal, pageable));
    }

    @GetMapping("/exercises/{id}") @PreAuthorize("@permissionPolicy.canAccessLibraryAdministration(authentication)") public ExerciseTemplateResponse exercise(@PathVariable UUID id) { return service.getExercise(id); }
    @PostMapping("/exercises") @PreAuthorize("@permissionPolicy.has(authentication, 'library.draft.create')") public ResponseEntity<ExerciseTemplateResponse> createExercise(
            @Valid @RequestBody ExerciseTemplateRequest request, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        var result = service.createExercise(request, actor(jwt), canManageAny(authentication));
        return ResponseEntity.created(URI.create("/api/v1/admin/library/exercises/" + result.id())).body(result);
    }
    @PutMapping("/exercises/{id}") @PreAuthorize("@permissionPolicy.has(authentication, 'library.draft.update_own')") public ExerciseTemplateResponse updateExercise(@PathVariable UUID id, @Valid @RequestBody ExerciseTemplateRequest request, @AuthenticationPrincipal Jwt jwt, Authentication authentication) { return service.updateExercise(id, request, actor(jwt), canManageAny(authentication)); }
    @DeleteMapping("/exercises/{id}") @PreAuthorize("@permissionPolicy.has(authentication, 'library.draft.update_own')") public ResponseEntity<Void> archiveExercise(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) { service.archiveExercise(id, actor(jwt), canManageAny(authentication)); return ResponseEntity.noContent().build(); }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean canManageAny(Authentication authentication) {
        return permissionPolicy.has(authentication, "library.publish");
    }
}
