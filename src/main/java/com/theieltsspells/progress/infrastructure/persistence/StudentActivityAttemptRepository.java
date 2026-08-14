package com.theieltsspells.progress.infrastructure.persistence;

import com.theieltsspells.progress.domain.StudentActivityAttempt;
import com.theieltsspells.shared.persistence.enums.ActivityAttemptStatus;
import com.theieltsspells.shared.persistence.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentActivityAttemptRepository extends JpaRepository<StudentActivityAttempt, UUID> {

    Optional<StudentActivityAttempt> findByClassActivityIdAndStudentIdAndAttemptNo(
            UUID classActivityId, UUID studentId, Short attemptNo);

    List<StudentActivityAttempt> findByClassActivityIdAndStudentIdOrderByAttemptNoDesc(
            UUID classActivityId, UUID studentId);

    Page<StudentActivityAttempt> findByStudentId(UUID studentId, Pageable pageable);

    Page<StudentActivityAttempt> findByStudentIdAndStatus(
            UUID studentId, ActivityAttemptStatus status, Pageable pageable);

    Page<StudentActivityAttempt> findByReviewStatus(ReviewStatus reviewStatus, Pageable pageable);

    List<StudentActivityAttempt> findByClassActivityIdInOrderByAttemptNoDesc(List<UUID> classActivityIds);
}
