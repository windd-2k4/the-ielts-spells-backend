package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

public record TestBankRequest(
        @NotBlank String title, String description, @NotNull SkillType skill,
        @NotBlank String testType,
        @Min(1) Integer durationMinutes, String version, List<String> tags,
        Map<String, Object> builderContent,
        @Positive Integer draftRevision
) {}
