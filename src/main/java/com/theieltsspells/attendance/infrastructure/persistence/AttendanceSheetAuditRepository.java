package com.theieltsspells.attendance.infrastructure.persistence;

import com.theieltsspells.attendance.domain.AttendanceSheetAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AttendanceSheetAuditRepository extends JpaRepository<AttendanceSheetAudit, UUID> {}
