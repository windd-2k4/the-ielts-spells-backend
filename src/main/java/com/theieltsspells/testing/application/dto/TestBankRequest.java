package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record TestBankRequest(
        @NotBlank String title, String description, @NotNull SkillType skill,
        @NotBlank String purpose, @NotBlank String testType, String difficulty,
        @Min(1) Integer durationMinutes, String version, List<String> tags,
        Map<String, Object> builderContent
) {}
