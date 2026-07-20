package com.theieltsspells.assignment.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.academic.domain.Class;
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
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class Assignment {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "assignment_type", nullable = false)
    private String assignmentType;

    @Column(name = "opens_at")
    private OffsetDateTime opensAt;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "max_score")
    private BigDecimal maxScore;

    @Column(name = "allow_resubmission", nullable = false)
    private Boolean allowResubmission;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private AssignmentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", insertable = false, updatable = false)
    private Class classRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private Profile createdByRef;
}
