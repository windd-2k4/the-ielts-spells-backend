package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.Enrollment;
import com.theieltsspells.shared.persistence.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    Page<Enrollment> findByCourseId(UUID courseId, Pageable pageable);

    List<Enrollment> findByCourseIdAndStatus(UUID courseId, EnrollmentStatus status);

    @EntityGraph(attributePaths = {"studentRef", "studentRef.userRef"})
    List<Enrollment> findByCourseId(UUID courseId);

    Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable);

    boolean existsByCourseIdAndStudentId(UUID courseId, UUID studentId);

    long countByCourseIdAndStatusIn(UUID courseId, List<EnrollmentStatus> statuses);

}
