package com.theieltsspells.academic.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_session_items")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class CourseSessionItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "item_type", nullable = false)
    private String itemType;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "source_assignment_id")
    private UUID sourceAssignmentId;

    @Column(name = "source_test_id")
    private UUID sourceTestId;

    @Column(name = "source_resource_id")
    private UUID sourceResourceId;

    @Column(name = "source_exercise_template_id")
    private UUID sourceExerciseTemplateId;

    @Column(name = "deadline_at")
    private OffsetDateTime deadlineAt;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "visibility", nullable = false)
    private String visibility;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
