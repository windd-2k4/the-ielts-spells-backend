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
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class Question {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Column(name = "prompt", nullable = false)
    private String prompt;

    @Column(name = "media_path")
    private String mediaPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer")
    private Map<String, Object> correctAnswer;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", insertable = false, updatable = false)
    private TestSection sectionRef;
}
