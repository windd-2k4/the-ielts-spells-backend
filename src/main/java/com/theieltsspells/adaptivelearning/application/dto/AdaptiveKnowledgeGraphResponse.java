package com.theieltsspells.adaptivelearning.application.dto;

import java.util.List;

public record AdaptiveKnowledgeGraphResponse(
        boolean enabled,
        List<Component> components,
        List<Relation> relations
) {
    public record Component(
            String code,
            String name,
            String description,
            String skill,
            boolean active,
            int questionTypeMappings,
            int questionMappings,
            int contentMappings,
            int learnersWithEvidence
    ) {
    }

    public record Relation(
            String prerequisiteCode,
            String targetCode,
            String relationType
    ) {
    }
}
