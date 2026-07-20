package com.theieltsspells.progress.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.identity.domain.TeacherProfile;
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
@Table(name = "teacher_activity_reviews")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class TeacherActivityReview {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private ReviewStatus status;

    @Column(name = "verified_score")
    private BigDecimal verifiedScore;

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "private_note")
    private String privateNote;

    @Column(name = "adjustment_reason")
    private String adjustmentReason;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", insertable = false, updatable = false)
    private StudentActivityAttempt attemptRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", insertable = false, updatable = false)
    private TeacherProfile reviewerRef;
}
