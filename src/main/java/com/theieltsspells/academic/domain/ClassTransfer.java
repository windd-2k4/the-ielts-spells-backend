package com.theieltsspells.academic.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "course_transfers")
@Getter @Setter @NoArgsConstructor @DynamicInsert
public class ClassTransfer {
    @Id @GeneratedValue @UuidGenerator private UUID id;
    @Column(name = "source_enrollment_id", nullable = false) private UUID sourceEnrollmentId;
    @Column(name = "target_course_id", nullable = false) private UUID targetCourseId;
    @Column(name = "target_enrollment_id") private UUID targetEnrollmentId;
    @Column(name = "reservation_id") private UUID reservationId;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String reason;
    @Column(name = "fee_adjustment", nullable = false) private BigDecimal feeAdjustment;
    @Column(name = "requested_at", nullable = false) private OffsetDateTime requestedAt;
    @Column(name = "approved_at") private OffsetDateTime approvedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    private String notes;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
}
