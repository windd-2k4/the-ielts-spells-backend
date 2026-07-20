package com.theieltsspells.writingevaluation.domain;

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
@Table(name = "writing_criterion_scores")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class WritingCriterionScore {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "evaluation_id", nullable = false)
    private UUID evaluationId;

    @Column(name = "criterion", nullable = false)
    private String criterion;

    @Column(name = "ai_band", nullable = false)
    private BigDecimal aiBand;

    @Column(name = "teacher_band")
    private BigDecimal teacherBand;

    @Column(name = "ai_feedback")
    private String aiFeedback;

    @Column(name = "teacher_feedback")
    private String teacherFeedback;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false)
    private Map<String, Object> evidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id", insertable = false, updatable = false)
    private WritingEvaluation evaluationRef;
}
