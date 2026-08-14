package com.theieltsspells.academic.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_templates")
@Getter
@Setter
public class ScheduleTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(name = "skill_pair", nullable = false)
    private String skillPair;
    private String description;
    @Column(name = "definition_json", nullable = false, columnDefinition = "text")
    private String definitionJson;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
