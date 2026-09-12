package com.theieltsspells.learninglibrary.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ContentHubDashboardResponse(
        ContentHubSummaryResponse summary,
        List<RecentResource> recentResources,
        List<DraftTest> draftTests
) {
    public record RecentResource(
            UUID id,
            String code,
            String title,
            String description,
            SkillType skill,
            OffsetDateTime updatedAt
    ) {
    }

    public record DraftTest(
            UUID id,
            String code,
            String title,
            SkillType skill,
            int totalQuestions,
            OffsetDateTime updatedAt
    ) {
    }
}
