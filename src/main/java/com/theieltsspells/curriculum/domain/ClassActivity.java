package com.theieltsspells.curriculum.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.academic.domain.Class;
import com.theieltsspells.academic.domain.ClassSession;
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
@Table(name = "class_activities")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class ClassActivity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "test_assignment_id")
    private UUID testAssignmentId;

    @Column(name = "opens_at")
    private OffsetDateTime opensAt;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "is_required_override")
    private Boolean isRequiredOverride;

    @Column(name = "max_score_override")
    private BigDecimal maxScoreOverride;

    @Column(name = "instructions_override")
    private String instructionsOverride;

    @Column(name = "is_published", nullable = false)
    private Boolean isPublished;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", insertable = false, updatable = false)
    private Class classRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private ClassSession sessionRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", insertable = false, updatable = false)
    private LearningActivity activityRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private Profile createdByRef;
}
