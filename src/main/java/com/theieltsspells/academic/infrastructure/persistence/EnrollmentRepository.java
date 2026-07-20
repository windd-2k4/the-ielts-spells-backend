package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.Enrollment;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByClassIdAndStudentId(UUID classId, UUID studentId);

    Page<Enrollment> findByClassId(UUID classId, Pageable pageable);

    List<Enrollment> findByClassIdAndStatus(UUID classId, EnrollmentStatus status);

    Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable);

    boolean existsByClassIdAndStudentId(UUID classId, UUID studentId);

    long countByClassIdAndStatusIn(UUID classId, List<EnrollmentStatus> statuses);
}
