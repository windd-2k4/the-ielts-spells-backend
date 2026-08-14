package com.theieltsspells.assignment.infrastructure.persistence;

import com.theieltsspells.assignment.domain.Assignment;
import com.theieltsspells.shared.persistence.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Page<Assignment> findByCourseId(UUID courseId, Pageable pageable);

    List<Assignment> findByCourseIdAndStatusOrderByDueAtAsc(UUID courseId, AssignmentStatus status);

    List<Assignment> findByDueAtBetweenOrderByDueAtAsc(OffsetDateTime from, OffsetDateTime to);
}
