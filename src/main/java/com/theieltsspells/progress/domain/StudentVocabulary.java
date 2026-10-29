package com.theieltsspells.progress.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.identity.domain.StudentProfile;
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
@Table(name = "student_vocabulary")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class StudentVocabulary {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "term", nullable = false)
    private String term;

    @Column(name = "meaning")
    private String meaning;

    @Column(name = "example_sentence")
    private String exampleSentence;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @Column(name = "note")
    private String note;

    @Column(name = "source_label")
    private String sourceLabel;

    @Column(name = "mastery_level", nullable = false)
    private Short masteryLevel;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_reviewed_at")
    private OffsetDateTime lastReviewedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", insertable = false, updatable = false)
    private StudentProfile studentRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", insertable = false, updatable = false)
    private StudentActivityAttempt attemptRef;
}
