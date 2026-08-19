package com.theieltsspells.learninglibrary.domain;

import com.theieltsspells.shared.persistence.enums.SkillType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "exercise_templates")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class ExerciseTemplate {
    @Id @GeneratedValue @UuidGenerator private UUID id;
    @Column(insertable = false, updatable = false) private String code;
    @Column(nullable = false) private String title;
    private String instructions;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(nullable = false) private SkillType skill;
    @Column(nullable = false) private String category;
    @Column(name = "exercise_type", nullable = false) private String exerciseType;
    @Column(name = "completion_mode", nullable = false) private String completionMode;
    @Column(nullable = false) private String scope;
    @Column(name = "course_id") private UUID courseId;
    @Column(name = "source_url") private String sourceUrl;
    @Column(name = "duration_minutes") private Short durationMinutes;
    @Column(name = "max_score") private BigDecimal maxScore;
    @Column(name = "attempt_limit", nullable = false) private Short attemptLimit;
    @Column(name = "requires_teacher_review", nullable = false) private Boolean requiresTeacherReview;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, Object> content = new LinkedHashMap<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "answer_key", nullable = false, columnDefinition = "jsonb") private Map<String, Object> answerKey = new LinkedHashMap<>();
    @Column(nullable = false) private String status;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", insertable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private OffsetDateTime updatedAt;
}
