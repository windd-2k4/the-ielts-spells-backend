package com.theieltsspells.academic.infrastructure.persistence;

import com.theieltsspells.academic.domain.CourseSessionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseSessionItemRepository extends JpaRepository<CourseSessionItem, UUID> {
    List<CourseSessionItem> findBySessionIdOrderByDisplayOrderAscCreatedAtAsc(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
