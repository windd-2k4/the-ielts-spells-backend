package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.EnrollmentReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnrollmentReservationRepository extends JpaRepository<EnrollmentReservation, UUID> {
    boolean existsByEnrollmentIdAndStatus(UUID enrollmentId, String status);
    List<EnrollmentReservation> findByEnrollmentIdOrderByRequestedAtDesc(UUID enrollmentId);
}

