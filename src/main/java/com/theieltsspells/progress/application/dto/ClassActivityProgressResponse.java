package com.theieltsspells.progress.application.dto;

import com.theieltsspells.shared.persistence.enums.ActivityType;
import com.theieltsspells.shared.persistence.enums.CompletionMethod;
import com.theieltsspells.shared.persistence.enums.SkillType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClassActivityProgressResponse(UUID classActivityId, UUID sessionId, UUID activityId,
        String title, SkillType skill, ActivityType activityType, CompletionMethod completionMethod,
        OffsetDateTime opensAt, OffsetDateTime dueAt, boolean required,
        List<ActivityAttemptResponse> attempts) {}
