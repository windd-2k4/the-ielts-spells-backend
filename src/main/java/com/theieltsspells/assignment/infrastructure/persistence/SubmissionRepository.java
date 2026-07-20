package com.theieltsspells.assignment.infrastructure.persistence;

import com.theieltsspells.assignment.domain.Submission;
import com.theieltsspells.shared.persistence.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByAssignmentIdAndStudentIdAndAttemptNo(UUID assignmentId, UUID studentId, Short attemptNo);

    List<Submission> findByAssignmentIdAndStudentIdOrderByAttemptNoDesc(UUID assignmentId, UUID studentId);

    Page<Submission> findByAssignmentId(UUID assignmentId, Pageable pageable);

    Page<Submission> findByStudentId(UUID studentId, Pageable pageable);

    List<Submission> findByAssignmentIdAndStatus(UUID assignmentId, SubmissionStatus status);
}
