package com.theieltsspells.learninglibrary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.learninglibrary.domain.LearningResource;
import com.theieltsspells.learninglibrary.infrastructure.persistence.ExerciseTemplateRepository;
import com.theieltsspells.learninglibrary.infrastructure.persistence.LearningResourceFileRepository;
import com.theieltsspells.learninglibrary.infrastructure.persistence.LearningResourceRepository;
import com.theieltsspells.learninglibrary.infrastructure.storage.FileStorageService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningLibraryApplicationServiceTests {

    private final LearningResourceRepository resources = mock(LearningResourceRepository.class);
    private final ExerciseTemplateRepository exercises = mock(ExerciseTemplateRepository.class);
    private final LearningResourceFileRepository resourceFiles = mock(LearningResourceFileRepository.class);
    private final FileStorageService fileStorage = mock(FileStorageService.class);
    private final jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LearningLibraryApplicationService service = new LearningLibraryApplicationService(
            resources,
            exercises,
            resourceFiles,
            fileStorage,
            entityManager,
            jdbc,
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void loadsContentHubDashboardInOneDatabaseRoundTrip() {
        when(jdbc.queryForObject(any(String.class), eq(String.class))).thenReturn("""
                {
                  "summary": {
                    "resources": 3,
                    "tests": 2,
                    "media": 1,
                    "awaitingReview": 1,
                    "inUse": 2
                  },
                  "recentResources": [],
                  "draftTests": []
                }
                """);

        var result = service.dashboard();

        assertThat(result.summary().resources()).isEqualTo(3);
        assertThat(result.recentResources()).isEmpty();
        assertThat(result.draftTests()).isEmpty();
        verify(jdbc).queryForObject(any(String.class), eq(String.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void excludesMediaRecordsFromTheLearningResourceList() {
        when(resources.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());

        service.listResources(null, null, null, null, null, null, false, PageRequest.of(0, 20));

        var specification = ArgumentCaptor.forClass(Specification.class);
        verify(resources).findAll(specification.capture(), any(PageRequest.class));

        Root<LearningResource> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<String> category = mock(Path.class);
        when(root.get("category")).thenReturn((Path) category);

        specification.getValue().toPredicate(root, query, builder);

        verify(builder).notEqual(category, "MEDIA");
    }

    @Test
    void uploadMedia_createsMediaResourceAndStoresFileDirectly() {
        var actor = java.util.UUID.randomUUID();
        var file = new org.springframework.mock.web.MockMultipartFile("file", "listening-part1.mp3", "audio/mpeg", new byte[]{1, 2, 3});
        var resourceId = java.util.UUID.randomUUID();
        var fileId = java.util.UUID.randomUUID();

        when(resources.saveAndFlush(any(com.theieltsspells.learninglibrary.domain.LearningResource.class))).thenAnswer(inv -> {
            com.theieltsspells.learninglibrary.domain.LearningResource r = inv.getArgument(0);
            r.setId(resourceId);
            return r;
        });

        when(fileStorage.store(any(String.class), any())).thenReturn(
                new com.theieltsspells.shared.storage.FileStorage.StoredFile("LOCAL", null, "resources/" + resourceId + "/test.mp3")
        );

        when(resourceFiles.saveAndFlush(any(com.theieltsspells.learninglibrary.domain.LearningResourceFile.class))).thenAnswer(inv -> {
            com.theieltsspells.learninglibrary.domain.LearningResourceFile f = inv.getArgument(0);
            f.setId(fileId);
            return f;
        });

        jakarta.persistence.Query query = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        when(query.setParameter(any(String.class), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(java.util.List.of());

        var result = service.uploadMedia(file, actor);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(fileId);
        assertThat(result.mimeType()).isEqualTo("AUDIO");
        verify(resources).saveAndFlush(any(com.theieltsspells.learninglibrary.domain.LearningResource.class));
        verify(resourceFiles).saveAndFlush(any(com.theieltsspells.learninglibrary.domain.LearningResourceFile.class));
    }

    @Test
    void deleteMedia_deletesFileAndMediaResourceCleanly() {
        var actor = java.util.UUID.randomUUID();
        var fileId = java.util.UUID.randomUUID();
        var resourceId = java.util.UUID.randomUUID();

        var file = new com.theieltsspells.learninglibrary.domain.LearningResourceFile();
        file.setId(fileId);
        file.setResourceId(resourceId);

        var resource = new com.theieltsspells.learninglibrary.domain.LearningResource();
        resource.setId(resourceId);
        resource.setCreatedBy(actor);
        resource.setStatus("PUBLISHED");

        when(resourceFiles.findById(fileId)).thenReturn(java.util.Optional.of(file));
        when(resources.findById(resourceId)).thenReturn(java.util.Optional.of(resource));

        jakarta.persistence.Query countQuery = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(countQuery);
        when(countQuery.setParameter(eq("id"), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);

        service.deleteMedia(fileId, actor, false);

        verify(fileStorage).delete(file);
        verify(resourceFiles).delete(file);
        verify(resources).delete(resource);
    }
}
