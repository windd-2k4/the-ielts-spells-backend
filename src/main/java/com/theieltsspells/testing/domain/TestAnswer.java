package com.theieltsspells.testing.domain;

import com.theieltsspells.shared.persistence.enums.*;
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
@Table(name = "test_answers")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class TestAnswer {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", nullable = false)
    private Map<String, Object> answer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "auto_score")
    private BigDecimal autoScore;

    @Column(name = "teacher_score")
    private BigDecimal teacherScore;

    @Column(name = "teacher_feedback")
    private String teacherFeedback;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", insertable = false, updatable = false)
    private TestAttempt attemptRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question questionRef;
}
