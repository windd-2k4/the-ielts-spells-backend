package com.theieltsspells.testing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** A student's answer keyed to an immutable TestVersion question identifier. */
@Entity
@Table(name = "test_attempt_responses", uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "question_key"}))
@Getter
@Setter
@NoArgsConstructor
public class TestAttemptResponse {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_key", nullable = false)
    private String questionKey;

    @Column(name = "question_type", nullable = false)
    private String questionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", nullable = false)
    private Map<String, Object> answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_answer", nullable = false)
    private java.util.List<String> normalizedAnswer;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "auto_score")
    private BigDecimal autoScore;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(name = "client_revision", nullable = false)
    private Integer clientRevision;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", insertable = false, updatable = false)
    private TestAttempt attempt;
}
