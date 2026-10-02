package com.theieltsspells.testing.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiTestParseRequest(
        @NotBlank String rawText,
        @NotNull SkillType skill,
        String testFormat,
        String titleHint,
        String sourceUrl,
        AiParserProvider provider,
        @Size(max = 2000, message = "Hướng dẫn cho AI không được vượt quá 2.000 ký tự")
        String teacherInstructions
) {}
