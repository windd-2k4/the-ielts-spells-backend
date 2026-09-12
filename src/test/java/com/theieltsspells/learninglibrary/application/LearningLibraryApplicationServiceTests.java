package com.theieltsspells.learninglibrary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningLibraryApplicationServiceTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LearningLibraryApplicationService service = new LearningLibraryApplicationService(
            null,
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
}
