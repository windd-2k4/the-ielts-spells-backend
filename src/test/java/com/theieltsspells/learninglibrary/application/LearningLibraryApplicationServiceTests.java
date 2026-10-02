package com.theieltsspells.learninglibrary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.learninglibrary.domain.LearningResource;
import com.theieltsspells.learninglibrary.infrastructure.persistence.LearningResourceRepository;
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
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LearningLibraryApplicationService service = new LearningLibraryApplicationService(
            resources,
            null,
            null,
            null,
            null,
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
}
