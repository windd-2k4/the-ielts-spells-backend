package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {
    List<ClassSession> findByCourseIdOrderBySessionNo(UUID courseId);
    Optional<ClassSession> findByIdAndCourseId(UUID id, UUID courseId);
    boolean existsByCourseIdAndSessionNo(UUID courseId, Short sessionNo);
}
