package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.Class;
import com.theieltsspells.shared.persistence.enums.ClassStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassRepository extends JpaRepository<Class, UUID> {

    Optional<Class> findByCodeIgnoreCase(String code);

    Page<Class> findByCourseId(UUID courseId, Pageable pageable);

    Page<Class> findByStatus(ClassStatus status, Pageable pageable);

    List<Class> findByStartsOnBetweenOrderByStartsOnAsc(LocalDate from, LocalDate to);

    boolean existsByCodeIgnoreCase(String code);
}
