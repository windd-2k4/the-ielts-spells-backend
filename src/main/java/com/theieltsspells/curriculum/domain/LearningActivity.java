package com.theieltsspells.curriculum.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.identity.domain.Profile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "learning_activities")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class LearningActivity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "skill", nullable = false)
    private SkillType skill;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "completion_method", nullable = false)
    private CompletionMethod completionMethod;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "default_max_score")
    private BigDecimal defaultMaxScore;

    @Column(name = "expected_duration_minutes")
    private Short expectedDurationMinutes;

    @Column(name = "requires_teacher_review", nullable = false)
    private Boolean requiresTeacherReview;

    @Column(name = "requires_error_analysis", nullable = false)
    private Boolean requiresErrorAnalysis;

    @Column(name = "requires_comprehension_rating", nullable = false)
    private Boolean requiresComprehensionRating;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_evidence_types", nullable = false)
    private List<String> allowedEvidenceTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false)
    private Map<String, Object> configuration;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", insertable = false, updatable = false)
    private CourseModule moduleRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private Profile createdByRef;
}
