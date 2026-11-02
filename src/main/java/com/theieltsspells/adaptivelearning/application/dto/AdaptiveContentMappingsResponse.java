package com.theieltsspells.adaptivelearning.application.dto;

import java.util.List;
import java.util.UUID;

public record AdaptiveContentMappingsResponse(
        boolean enabled,
        List<Component> components,
        List<Mapping> mappings
) {
    public record Component(String code, String name, String skill, boolean active) {
    }

    public record Mapping(
            UUID id,
            String componentCode,
            String componentName,
            String targetType,
            UUID targetId,
            String title,
            String href,
            Integer durationMinutes,
            String difficulty,
            int priority,
            boolean active
    ) {
    }
}
