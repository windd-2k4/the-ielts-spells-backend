package com.theieltsspells.writingevaluation.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.assignment.domain.Submission;
import com.theieltsspells.identity.domain.Profile;
import com.theieltsspells.testing.domain.TestAnswer;
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
@Table(name = "writing_evaluations")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class WritingEvaluation {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(name = "test_answer_id")
    private UUID testAnswerId;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "essay_text", nullable = false)
    private String essayText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private EvaluationStatus status;

    @Column(name = "model_provider")
    private String modelProvider;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "rubric_version", nullable = false)
    private String rubricVersion;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "overall_band_ai")
    private BigDecimal overallBandAi;

    @Column(name = "overall_band_teacher")
    private BigDecimal overallBandTeacher;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", nullable = false)
    private Map<String, Object> strengths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvements", nullable = false)
    private Map<String, Object> improvements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_errors", nullable = false)
    private Map<String, Object> detectedErrors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response")
    private Map<String, Object> rawResponse;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "processing_ms")
    private Integer processingMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", insertable = false, updatable = false)
    private Submission submissionRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_answer_id", insertable = false, updatable = false)
    private TestAnswer testAnswerRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", insertable = false, updatable = false)
    private Profile reviewedByRef;
}
