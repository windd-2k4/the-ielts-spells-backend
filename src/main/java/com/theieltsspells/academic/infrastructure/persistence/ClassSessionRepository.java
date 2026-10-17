package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {
    List<ClassSession> findByCourseIdOrderBySessionNo(UUID courseId);

    @Query("select session from ClassSession session left join fetch session.teacherRef " +
            "where session.courseId = :courseId order by session.sessionNo")
    List<ClassSession> findByCourseIdWithTeacherOrderBySessionNo(@Param("courseId") UUID courseId);

    Optional<ClassSession> findByIdAndCourseId(UUID id, UUID courseId);
    boolean existsByCourseIdAndSessionNo(UUID courseId, Short sessionNo);
}
