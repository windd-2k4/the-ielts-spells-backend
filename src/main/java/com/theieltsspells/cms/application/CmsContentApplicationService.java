package com.theieltsspells.cms.application;

import com.theieltsspells.cms.application.dto.CampaignRequest;
import com.theieltsspells.cms.application.dto.CampaignResponse;
import com.theieltsspells.cms.application.dto.CmsBannerRequest;
import com.theieltsspells.cms.application.dto.CmsBannerResponse;
import com.theieltsspells.cms.application.dto.CmsPageRequest;
import com.theieltsspells.cms.application.dto.CmsPageResponse;
import com.theieltsspells.cms.application.dto.CmsPostRequest;
import com.theieltsspells.cms.application.dto.CmsPostResponse;
import com.theieltsspells.cms.application.dto.CmsPublicationRequest;
import com.theieltsspells.cms.domain.Campaign;
import com.theieltsspells.cms.domain.CmsBanner;
import com.theieltsspells.cms.domain.CmsPage;
import com.theieltsspells.cms.domain.CmsPost;
import com.theieltsspells.cms.infrastructure.persistence.CampaignRepository;
import com.theieltsspells.cms.infrastructure.persistence.CmsBannerRepository;
import com.theieltsspells.cms.infrastructure.persistence.CmsPageRepository;
import com.theieltsspells.cms.infrastructure.persistence.CmsPostRepository;
import com.theieltsspells.shared.application.BusinessRuleException;
import com.theieltsspells.shared.application.ConflictException;
import com.theieltsspells.shared.application.ResourceNotFoundException;
import com.theieltsspells.shared.persistence.enums.PublishStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Back-office CMS workflow for Social Media and administrators. The public
 * content surface remains a separate read model and never exposes drafts.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CmsContentApplicationService {

    private final CmsPageRepository pages;
    private final CmsPostRepository posts;
    private final CmsBannerRepository banners;
    private final CampaignRepository campaigns;

    public Page<CmsPageResponse> pages(PublishStatus status, Pageable pageable) {
        return (status == null ? pages.findAll(pageable) : pages.findByStatus(status, pageable)).map(this::page);
    }

    public CmsPageResponse page(UUID id) {
        return page(requirePage(id));
    }

    @Transactional
    public CmsPageResponse createPage(CmsPageRequest request, UUID actorId) {
        String slug = slug(request.slug());
        requirePageSlugAvailable(slug, null);
        OffsetDateTime now = OffsetDateTime.now();
        var value = new CmsPage();
        value.setSlug(slug);
        applyPage(value, request);
        value.setStatus(PublishStatus.DRAFT);
        value.setCreatedBy(actorId);
        value.setUpdatedBy(actorId);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return page(pages.save(value));
    }

    @Transactional
    public CmsPageResponse updatePage(UUID id, CmsPageRequest request, UUID actorId) {
        var value = requirePage(id);
        String slug = slug(request.slug());
        requirePageSlugAvailable(slug, id);
        value.setSlug(slug);
        applyPage(value, request);
        value.setUpdatedBy(actorId);
        value.setUpdatedAt(OffsetDateTime.now());
        return page(value);
    }

    @Transactional
    public CmsPageResponse publishPage(UUID id, CmsPublicationRequest request, UUID actorId) {
        var value = requirePage(id);
        value.setStatus(request.status());
        value.setPublishedAt(publicationTime(request));
        value.setUpdatedBy(actorId);
        value.setUpdatedAt(OffsetDateTime.now());
        return page(value);
    }

    public Page<CmsPostResponse> posts(PublishStatus status, Pageable pageable) {
        return (status == null ? posts.findAll(pageable) : posts.findByStatus(status, pageable)).map(this::post);
    }

    public CmsPostResponse post(UUID id) {
        return post(requirePost(id));
    }

    @Transactional
    public CmsPostResponse createPost(CmsPostRequest request, UUID actorId) {
        String slug = slug(request.slug());
        requirePostSlugAvailable(slug, null);
        OffsetDateTime now = OffsetDateTime.now();
        var value = new CmsPost();
        value.setSlug(slug);
        applyPost(value, request);
        value.setStatus(PublishStatus.DRAFT);
        value.setAuthorId(actorId);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return post(posts.save(value));
    }

    @Transactional
    public CmsPostResponse updatePost(UUID id, CmsPostRequest request) {
        var value = requirePost(id);
        String slug = slug(request.slug());
        requirePostSlugAvailable(slug, id);
        value.setSlug(slug);
        applyPost(value, request);
        value.setUpdatedAt(OffsetDateTime.now());
        return post(value);
    }

    @Transactional
    public CmsPostResponse publishPost(UUID id, CmsPublicationRequest request) {
        var value = requirePost(id);
        value.setStatus(request.status());
        value.setPublishedAt(publicationTime(request));
        value.setUpdatedAt(OffsetDateTime.now());
        return post(value);
    }

    public Page<CmsBannerResponse> banners(PublishStatus status, Pageable pageable) {
        return (status == null ? banners.findAll(pageable) : banners.findByStatus(status, pageable)).map(this::banner);
    }

    public CmsBannerResponse banner(UUID id) {
        return banner(requireBanner(id));
    }

    @Transactional
    public CmsBannerResponse createBanner(CmsBannerRequest request, UUID actorId) {
        OffsetDateTime now = OffsetDateTime.now();
        var value = new CmsBanner();
        applyBanner(value, request);
        value.setStatus(PublishStatus.DRAFT);
        value.setCreatedBy(actorId);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return banner(banners.save(value));
    }

    @Transactional
    public CmsBannerResponse updateBanner(UUID id, CmsBannerRequest request) {
        var value = requireBanner(id);
        applyBanner(value, request);
        value.setUpdatedAt(OffsetDateTime.now());
        return banner(value);
    }

    @Transactional
    public CmsBannerResponse publishBanner(UUID id, CmsPublicationRequest request) {
        var value = requireBanner(id);
        if (request.status() == PublishStatus.SCHEDULED
                && (value.getStartsAt() == null || !value.getStartsAt().isAfter(OffsetDateTime.now()))) {
            throw new BusinessRuleException("Banner lên lịch phải có thời điểm bắt đầu hiển thị trong tương lai");
        }
        value.setStatus(request.status());
        value.setUpdatedAt(OffsetDateTime.now());
        return banner(value);
    }

    public Page<CampaignResponse> campaigns(Boolean active, Pageable pageable) {
        return (active == null ? campaigns.findAll(pageable) : campaigns.findByIsActive(active, pageable)).map(this::campaign);
    }

    public CampaignResponse campaign(UUID id) {
        return campaign(requireCampaign(id));
    }

    @Transactional
    public CampaignResponse createCampaign(CampaignRequest request, UUID actorId) {
        String code = clean(request.campaignCode());
        requireCampaignCodeAvailable(code, null);
        OffsetDateTime now = OffsetDateTime.now();
        var value = new Campaign();
        applyCampaign(value, request, true);
        value.setCampaignCode(code);
        value.setCreatedBy(actorId);
        value.setCreatedAt(now);
        return campaign(campaigns.save(value));
    }

    @Transactional
    public CampaignResponse updateCampaign(UUID id, CampaignRequest request) {
        var value = requireCampaign(id);
        String code = clean(request.campaignCode());
        requireCampaignCodeAvailable(code, id);
        applyCampaign(value, request, Boolean.TRUE.equals(value.getIsActive()));
        value.setCampaignCode(code);
        return campaign(value);
    }

    private void applyPage(CmsPage value, CmsPageRequest request) {
        value.setTitle(request.title().trim());
        value.setExcerpt(clean(request.excerpt()));
        value.setContent(json(request.content()));
        value.setSeoMetadata(json(request.seoMetadata()));
    }

    private void applyPost(CmsPost value, CmsPostRequest request) {
        value.setTitle(request.title().trim());
        value.setExcerpt(clean(request.excerpt()));
        value.setContent(json(request.content()));
        value.setCoverPath(clean(request.coverPath()));
        value.setTags(tags(request.tags()));
    }

    private void applyBanner(CmsBanner value, CmsBannerRequest request) {
        validateWindow(request.startsAt(), request.endsAt());
        if (request.campaignId() != null) {
            requireCampaign(request.campaignId());
        }
        value.setTitle(request.title().trim());
        value.setSubtitle(clean(request.subtitle()));
        value.setMediaPath(clean(request.mediaPath()));
        value.setTargetUrl(clean(request.targetUrl()));
        value.setPosition(request.position().trim());
        value.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        value.setStartsAt(request.startsAt());
        value.setEndsAt(request.endsAt());
        value.setCampaignId(request.campaignId());
    }

    private void applyCampaign(Campaign value, CampaignRequest request, boolean existingDefault) {
        validateWindow(request.startsAt(), request.endsAt());
        value.setName(request.name().trim());
        value.setSource(clean(request.source()));
        value.setMedium(clean(request.medium()));
        value.setStartsAt(request.startsAt());
        value.setEndsAt(request.endsAt());
        value.setBudget(request.budget());
        value.setIsActive(request.active() == null ? existingDefault : request.active());
    }

    private OffsetDateTime publicationTime(CmsPublicationRequest request) {
        return switch (request.status()) {
            case PUBLISHED -> request.publishedAt() == null ? OffsetDateTime.now() : request.publishedAt();
            case SCHEDULED -> {
                if (request.publishedAt() == null || !request.publishedAt().isAfter(OffsetDateTime.now())) {
                    throw new BusinessRuleException("Nội dung lên lịch phải có thời điểm xuất bản trong tương lai");
                }
                yield request.publishedAt();
            }
            case DRAFT, ARCHIVED -> null;
        };
    }

    private CmsPage requirePage(UUID id) {
        return pages.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trang CMS"));
    }

    private CmsPost requirePost(UUID id) {
        return posts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài viết CMS"));
    }

    private CmsBanner requireBanner(UUID id) {
        return banners.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy banner CMS"));
    }

    private Campaign requireCampaign(UUID id) {
        return campaigns.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chiến dịch"));
    }

    private void requirePageSlugAvailable(String slug, UUID id) {
        pages.findBySlug(slug).filter(value -> !value.getId().equals(id))
                .ifPresent(value -> { throw new ConflictException("Slug trang CMS đã được sử dụng"); });
    }

    private void requirePostSlugAvailable(String slug, UUID id) {
        posts.findBySlug(slug).filter(value -> !value.getId().equals(id))
                .ifPresent(value -> { throw new ConflictException("Slug bài viết CMS đã được sử dụng"); });
    }

    private void requireCampaignCodeAvailable(String code, UUID id) {
        if (code == null) {
            return;
        }
        campaigns.findByCampaignCode(code).filter(value -> !value.getId().equals(id))
                .ifPresent(value -> { throw new ConflictException("Mã chiến dịch đã được sử dụng"); });
    }

    private static Map<String, Object> json(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private static List<String> tags(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return new ArrayList<>(values.stream()
                .map(CmsContentApplicationService::clean)
                .filter(value -> value != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private static String slug(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static void validateWindow(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BusinessRuleException("Thời điểm kết thúc phải sau thời điểm bắt đầu");
        }
    }

    private CmsPageResponse page(CmsPage value) {
        return new CmsPageResponse(value.getId(), value.getSlug(), value.getTitle(), value.getExcerpt(),
                value.getContent(), value.getSeoMetadata(), value.getStatus(), value.getPublishedAt(),
                value.getCreatedBy(), value.getUpdatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private CmsPostResponse post(CmsPost value) {
        return new CmsPostResponse(value.getId(), value.getSlug(), value.getTitle(), value.getExcerpt(),
                value.getContent(), value.getCoverPath(), value.getTags(), value.getStatus(), value.getPublishedAt(),
                value.getAuthorId(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private CmsBannerResponse banner(CmsBanner value) {
        return new CmsBannerResponse(value.getId(), value.getTitle(), value.getSubtitle(), value.getMediaPath(),
                value.getTargetUrl(), value.getPosition(), value.getDisplayOrder(), value.getStartsAt(), value.getEndsAt(),
                value.getStatus(), value.getCampaignId(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private CampaignResponse campaign(Campaign value) {
        return new CampaignResponse(value.getId(), value.getName(), value.getSource(), value.getMedium(),
                value.getCampaignCode(), value.getStartsAt(), value.getEndsAt(), value.getBudget(), value.getIsActive(),
                value.getCreatedBy(), value.getCreatedAt());
    }
}
