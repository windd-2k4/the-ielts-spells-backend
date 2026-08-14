package com.theieltsspells.attendance.infrastructure.persistence;

import com.theieltsspells.attendance.domain.AttendanceSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AttendanceSheetRepository extends JpaRepository<AttendanceSheet, UUID> {
    Optional<AttendanceSheet> findBySessionId(UUID sessionId);
    List<AttendanceSheet> findBySessionIdIn(List<UUID> sessionIds);
}
