package com.theieltsspells.progress.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateCourseActivityRequest(
        @NotNull UUID sessionId,
        @NotBlank @Size(max = 180) String title,
        @NotNull SkillType skill,
        OffsetDateTime dueAt
) {}
