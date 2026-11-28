package com.theieltsspells.cms.application;

import com.theieltsspells.cms.domain.CmsPage;
import com.theieltsspells.cms.domain.CmsPageRevision;
import com.theieltsspells.cms.application.dto.CmsPageRequest;
import com.theieltsspells.cms.application.dto.CmsPublicationRequest;
import com.theieltsspells.cms.infrastructure.persistence.CampaignRepository;
import com.theieltsspells.cms.infrastructure.persistence.CmsBannerRepository;
import com.theieltsspells.cms.infrastructure.persistence.CmsPageRepository;
import com.theieltsspells.cms.infrastructure.persistence.CmsPageRevisionRepository;
import com.theieltsspells.cms.infrastructure.persistence.CmsPostRepository;
import com.theieltsspells.shared.persistence.enums.PublishStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmsContentApplicationServiceTests {

    @Mock private CmsPageRepository pages;
    @Mock private CmsPageRevisionRepository pageRevisions;
    @Mock private CmsPostRepository posts;
    @Mock private CmsBannerRepository banners;
    @Mock private CampaignRepository campaigns;
    @InjectMocks private CmsContentApplicationService service;

    @Test
    void restoringRevisionCreatesNewDraftSnapshotWithoutChangingPublishedContent() {
        UUID pageId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var page = page(pageId);
        var publishedContent = Map.<String, Object>of("headline", "Public headline");
        page.setPublishedTitle("Public title");
        page.setPublishedContent(publishedContent);
        page.setPublishedSeoMetadata(Map.of("title", "Public SEO"));

        var revision = new CmsPageRevision();
        revision.setId(revisionId);
        revision.setPageId(pageId);
        revision.setRevisionNo(3);
        revision.setSlug("trang-chu");
        revision.setTitle("Safe old draft");
        revision.setExcerpt("Restored excerpt");
        revision.setContent(Map.of("headline", "Restored headline"));
        revision.setSeoMetadata(Map.of("title", "Restored SEO"));

        var latest = new CmsPageRevision();
        latest.setRevisionNo(7);
        when(pages.findByIdForUpdate(pageId)).thenReturn(Optional.of(page));
        when(pageRevisions.findByIdAndPageId(revisionId, pageId)).thenReturn(Optional.of(revision));
        when(pages.findBySlug("trang-chu")).thenReturn(Optional.of(page));
        when(pageRevisions.findTopByPageIdOrderByRevisionNoDesc(pageId)).thenReturn(Optional.of(latest));

        var response = service.restorePageRevision(pageId, revisionId, actorId);

        assertThat(response.title()).isEqualTo("Safe old draft");
        assertThat(response.content()).containsEntry("headline", "Restored headline");
        assertThat(response.status()).isEqualTo(PublishStatus.PUBLISHED);
        assertThat(page.getPublishedTitle()).isEqualTo("Public title");
        assertThat(page.getPublishedContent()).isSameAs(publishedContent);

        var saved = ArgumentCaptor.forClass(CmsPageRevision.class);
        verify(pageRevisions).save(saved.capture());
        assertThat(saved.getValue().getRevisionNo()).isEqualTo(8);
        assertThat(saved.getValue().getSource()).isEqualTo("RESTORED");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(actorId);
        assertThat(saved.getValue().getContent()).containsEntry("headline", "Restored headline");
    }

    @Test
    void savingSchemaV3DraftPreservesPublishedSnapshot() {
        UUID pageId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var page = page(pageId);
        var published = Map.<String, Object>of("schemaVersion", 2, "headline", "Published");
        page.setPublishedTitle("Published title");
        page.setPublishedContent(published);
        page.setPublishedSeoMetadata(Map.of("title", "Published SEO"));
        var draft = Map.<String, Object>of("schemaVersion", 3, "sections", java.util.List.of(Map.of("id", "hero")));
        when(pages.findByIdForUpdate(pageId)).thenReturn(Optional.of(page));
        when(pages.findBySlug("trang-chu")).thenReturn(Optional.of(page));
        when(pageRevisions.findTopByPageIdOrderByRevisionNoDesc(pageId)).thenReturn(Optional.empty());

        var response = service.updatePage(pageId, new CmsPageRequest("trang-chu", "Draft v3", "Draft excerpt", draft, Map.of()), actorId);

        assertThat(response.content()).containsEntry("schemaVersion", 3);
        assertThat(response.hasUnpublishedChanges()).isTrue();
        assertThat(page.getPublishedTitle()).isEqualTo("Published title");
        assertThat(page.getPublishedContent()).isSameAs(published);
        var revision = ArgumentCaptor.forClass(CmsPageRevision.class);
        verify(pageRevisions).save(revision.capture());
        assertThat(revision.getValue().getSource()).isEqualTo("SAVED");
        assertThat(revision.getValue().getContent()).containsEntry("schemaVersion", 3);
    }

    @Test
    void publishingCopiesDraftToPublicSnapshotWithoutSharingMutableMaps() {
        UUID pageId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var page = page(pageId);
        var draft = new java.util.HashMap<String, Object>();
        draft.put("schemaVersion", 3);
        draft.put("sections", java.util.List.of(Map.of("id", "hero")));
        page.setTitle("Published v3");
        page.setContent(draft);
        page.setSeoMetadata(Map.of("title", "SEO v3"));
        when(pages.findByIdForUpdate(pageId)).thenReturn(Optional.of(page));
        when(pageRevisions.findTopByPageIdOrderByRevisionNoDesc(pageId)).thenReturn(Optional.empty());

        service.publishPage(pageId, new CmsPublicationRequest(PublishStatus.PUBLISHED, null), actorId);

        assertThat(page.getPublishedContent()).isEqualTo(draft).isNotSameAs(draft);
        assertThat(page.getPublishedTitle()).isEqualTo("Published v3");
        assertThat(page.getStatus()).isEqualTo(PublishStatus.PUBLISHED);
        var revision = ArgumentCaptor.forClass(CmsPageRevision.class);
        verify(pageRevisions).save(revision.capture());
        assertThat(revision.getValue().getSource()).isEqualTo("PUBLISHED");
    }

    private static CmsPage page(UUID id) {
        var page = new CmsPage();
        page.setId(id);
        page.setSlug("trang-chu");
        page.setTitle("Current draft");
        page.setContent(Map.of("headline", "Current headline"));
        page.setSeoMetadata(Map.of());
        page.setStatus(PublishStatus.PUBLISHED);
        page.setCreatedAt(OffsetDateTime.now().minusDays(2));
        page.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        return page;
    }
}
