package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.ClassTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassTransferRepository extends JpaRepository<ClassTransfer, UUID> {
    boolean existsBySourceEnrollmentIdAndStatus(UUID enrollmentId, String status);
    List<ClassTransfer> findBySourceEnrollmentIdOrderByRequestedAtDesc(UUID enrollmentId);
}

