package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.CourseStudentSupport;
import com.theieltsspells.academic.domain.CourseStudentSupportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseStudentSupportRepository extends JpaRepository<CourseStudentSupport, CourseStudentSupportId> {

    boolean existsByCourseIdAndStudentSupportId(UUID courseId, UUID studentSupportId);

    List<CourseStudentSupport> findByCourseIdOrderByAssignedAtAsc(UUID courseId);

    List<CourseStudentSupport> findByStudentSupportIdOrderByAssignedAtDesc(UUID studentSupportId);
}
