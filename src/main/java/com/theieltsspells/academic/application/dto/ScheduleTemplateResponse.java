package com.theieltsspells.academic.application.dto;

import com.theieltsspells.shared.persistence.enums.SkillPair;

import java.util.List;
import java.util.UUID;

public record ScheduleTemplateResponse(
        UUID id, String name, SkillPair skillPair, String description,
        List<ScheduleTemplateEntryRequest> entries
) {}
