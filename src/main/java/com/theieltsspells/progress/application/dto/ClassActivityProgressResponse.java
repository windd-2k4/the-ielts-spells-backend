package com.theieltsspells.progress.application.dto;

import com.theieltsspells.shared.persistence.enums.*;
import java.time.OffsetDateTime;
import java.util.*;

public record ClassActivityProgressResponse(UUID classActivityId, UUID sessionId, UUID activityId,
        String title, SkillType skill, ActivityType activityType, CompletionMethod completionMethod,
        OffsetDateTime opensAt, OffsetDateTime dueAt, boolean required,
        List<ActivityAttemptResponse> attempts) {}
