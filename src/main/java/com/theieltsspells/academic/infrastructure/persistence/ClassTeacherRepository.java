package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.ClassTeacher;
import com.theieltsspells.academic.domain.ClassTeacherId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, ClassTeacherId> {

    boolean existsByCourseIdAndTeacherId(UUID courseId, UUID teacherId);

    Optional<ClassTeacher> findByCourseIdAndTeacherId(UUID courseId, UUID teacherId);

    Optional<ClassTeacher> findByCourseIdAndIsPrimaryTrue(UUID courseId);

    @EntityGraph(attributePaths = {"teacherRef", "teacherRef.userRef"})
    List<ClassTeacher> findByCourseIdOrderByIsPrimaryDescAssignedAtAsc(UUID courseId);
}
