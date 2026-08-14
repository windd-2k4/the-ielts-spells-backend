package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByCodeIgnoreCase(String code);

    Page<Course> findByIsActive(Boolean isActive, Pageable pageable);

    Page<Course> findByIsPublicTrueAndIsActiveTrue(Pageable pageable);

    Page<Course> findByStatusAndIsActiveTrue(ClassStatus status, Pageable pageable);

    List<Course> findByStartsOnBetweenAndIsActiveTrueOrderByStartsOnAsc(LocalDate from, LocalDate to);

    boolean existsByCodeIgnoreCase(String code);
}
