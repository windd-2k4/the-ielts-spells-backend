package com.theieltsspells.academic.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "enrollment_reservations")
@Getter @Setter @NoArgsConstructor @DynamicInsert
public class EnrollmentReservation {
    @Id @GeneratedValue @UuidGenerator private UUID id;
    @Column(name = "enrollment_id", nullable = false) private UUID enrollmentId;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String reason;
    @Column(name = "sessions_consumed", nullable = false) private Short sessionsConsumed;
    @Column(name = "sessions_remaining", nullable = false) private Short sessionsRemaining;
    @Column(name = "credit_amount", nullable = false) private BigDecimal creditAmount;
    @Column(name = "expires_on") private LocalDate expiresOn;
    @Column(name = "target_class_id") private UUID targetClassId;
    @Column(name = "requested_at", nullable = false) private OffsetDateTime requestedAt;
    @Column(name = "approved_at") private OffsetDateTime approvedAt;
    @Column(name = "approved_by") private UUID approvedBy;
    private String notes;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
}

