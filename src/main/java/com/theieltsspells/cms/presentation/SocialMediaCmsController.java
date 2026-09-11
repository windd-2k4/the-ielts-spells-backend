package com.theieltsspells.cms.presentation;

import com.theieltsspells.cms.application.CmsContentApplicationService;
import com.theieltsspells.cms.application.dto.CampaignRequest;
import com.theieltsspells.cms.application.dto.CampaignResponse;
import com.theieltsspells.cms.application.dto.CmsBannerRequest;
import com.theieltsspells.cms.application.dto.CmsBannerResponse;
import com.theieltsspells.cms.application.dto.CmsPageRequest;
import com.theieltsspells.cms.application.dto.CmsPageResponse;
import com.theieltsspells.cms.application.dto.CmsPostRequest;
import com.theieltsspells.cms.application.dto.CmsPostResponse;
import com.theieltsspells.cms.application.dto.CmsPublicationRequest;
import com.theieltsspells.shared.persistence.enums.PublishStatus;
import com.theieltsspells.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Authenticated CMS authoring API. It is intentionally separate from
 * /api/v1/public, which only exposes published content to visitors.
 */
@RestController
@RequestMapping("/api/v1/social-media/cms")
@RequiredArgsConstructor
@Tag(name = "Social Media - CMS")
@SecurityRequirement(name = "bearerAuth")
public class SocialMediaCmsController {

    private final CmsContentApplicationService service;

    @GetMapping("/pages")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    @Operation(summary = "Danh sách trang CMS, bao gồm bản nháp")
    public PageResponse<CmsPageResponse> pages(
            @RequestParam(required = false) PublishStatus status,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable
    ) {
        return PageResponse.from(service.pages(status, pageable));
    }

    @GetMapping("/pages/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    public CmsPageResponse page(@PathVariable UUID id) {
        return service.page(id);
    }

    @PostMapping("/pages")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.manage')")
    public ResponseEntity<CmsPageResponse> createPage(
            @Valid @RequestBody CmsPageRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = service.createPage(request, actor(jwt));
        return ResponseEntity.created(URI.create("/api/v1/social-media/cms/pages/" + result.id())).body(result);
    }

    @PutMapping("/pages/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.manage')")
    public CmsPageResponse updatePage(
            @PathVariable UUID id,
            @Valid @RequestBody CmsPageRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.updatePage(id, request, actor(jwt));
    }

    @PatchMapping("/pages/{id}/publication")
    @PreAuthorize("@permissionPolicy.canChangeCmsPublication(authentication, #request.status().name())")
    public CmsPageResponse publishPage(
            @PathVariable UUID id,
            @Valid @RequestBody CmsPublicationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.publishPage(id, request, actor(jwt));
    }

    @GetMapping("/posts")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    @Operation(summary = "Danh sách bài viết CMS, bao gồm bản nháp")
    public PageResponse<CmsPostResponse> posts(
            @RequestParam(required = false) PublishStatus status,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable
    ) {
        return PageResponse.from(service.posts(status, pageable));
    }

    @GetMapping("/posts/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    public CmsPostResponse post(@PathVariable UUID id) {
        return service.post(id);
    }

    @PostMapping("/posts")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.manage')")
    public ResponseEntity<CmsPostResponse> createPost(
            @Valid @RequestBody CmsPostRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = service.createPost(request, actor(jwt));
        return ResponseEntity.created(URI.create("/api/v1/social-media/cms/posts/" + result.id())).body(result);
    }

    @PutMapping("/posts/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.manage')")
    public CmsPostResponse updatePost(@PathVariable UUID id, @Valid @RequestBody CmsPostRequest request) {
        return service.updatePost(id, request);
    }

    @PatchMapping("/posts/{id}/publication")
    @PreAuthorize("@permissionPolicy.canChangeCmsPublication(authentication, #request.status().name())")
    public CmsPostResponse publishPost(@PathVariable UUID id, @Valid @RequestBody CmsPublicationRequest request) {
        return service.publishPost(id, request);
    }

    @GetMapping("/banners")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    @Operation(summary = "Danh sách banner CMS, bao gồm bản nháp")
    public PageResponse<CmsBannerResponse> banners(
            @RequestParam(required = false) PublishStatus status,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable
    ) {
        return PageResponse.from(service.banners(status, pageable));
    }

    @GetMapping("/banners/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    public CmsBannerResponse banner(@PathVariable UUID id) {
        return service.banner(id);
    }

    @PostMapping("/banners")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.manage')")
    public ResponseEntity<CmsBannerResponse> createBanner(
            @Valid @RequestBody CmsBannerRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = service.createBanner(request, actor(jwt));
        return ResponseEntity.created(URI.create("/api/v1/social-media/cms/banners/" + result.id())).body(result);
    }

    @PutMapping("/banners/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.manage')")
    public CmsBannerResponse updateBanner(@PathVariable UUID id, @Valid @RequestBody CmsBannerRequest request) {
        return service.updateBanner(id, request);
    }

    @PatchMapping("/banners/{id}/publication")
    @PreAuthorize("@permissionPolicy.canChangeCmsPublication(authentication, #request.status().name())")
    public CmsBannerResponse publishBanner(@PathVariable UUID id, @Valid @RequestBody CmsPublicationRequest request) {
        return service.publishBanner(id, request);
    }

    @GetMapping("/campaigns")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    @Operation(summary = "Danh sách chiến dịch marketing")
    public PageResponse<CampaignResponse> campaigns(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return PageResponse.from(service.campaigns(active, pageable));
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'cms.content.read')")
    public CampaignResponse campaign(@PathVariable UUID id) {
        return service.campaign(id);
    }

    @PostMapping("/campaigns")
    @PreAuthorize("@permissionPolicy.has(authentication, 'marketing_media.manage')")
    public ResponseEntity<CampaignResponse> createCampaign(
            @Valid @RequestBody CampaignRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var result = service.createCampaign(request, actor(jwt));
        return ResponseEntity.created(URI.create("/api/v1/social-media/cms/campaigns/" + result.id())).body(result);
    }

    @PutMapping("/campaigns/{id}")
    @PreAuthorize("@permissionPolicy.has(authentication, 'marketing_media.manage')")
    public CampaignResponse updateCampaign(@PathVariable UUID id, @Valid @RequestBody CampaignRequest request) {
        return service.updateCampaign(id, request);
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
