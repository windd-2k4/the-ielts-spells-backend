package com.theieltsspells.writingevaluation.infrastructure.persistence;

import com.theieltsspells.shared.persistence.enums.EvaluationStatus;
import com.theieltsspells.writingevaluation.domain.WritingEvaluation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WritingEvaluationRepository extends JpaRepository<WritingEvaluation, UUID> {

    Optional<WritingEvaluation> findFirstBySubmissionIdOrderByCreatedAtDesc(UUID submissionId);

    Optional<WritingEvaluation> findFirstByTestAnswerIdOrderByCreatedAtDesc(UUID testAnswerId);

    Page<WritingEvaluation> findByStatus(EvaluationStatus status, Pageable pageable);

    List<WritingEvaluation> findByReviewedBy(UUID reviewerId);
}
