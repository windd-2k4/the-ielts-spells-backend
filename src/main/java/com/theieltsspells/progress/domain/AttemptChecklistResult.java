package com.theieltsspells.progress.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.curriculum.domain.ActivityChecklistItem;
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
@Table(name = "attempt_checklist_results")
@IdClass(AttemptChecklistResultId.class)
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class AttemptChecklistResult {

    @Id
    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Id
    @Column(name = "checklist_item_id", nullable = false)
    private UUID checklistItemId;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", insertable = false, updatable = false)
    private StudentActivityAttempt attemptRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_item_id", insertable = false, updatable = false)
    private ActivityChecklistItem checklistItemRef;
}
