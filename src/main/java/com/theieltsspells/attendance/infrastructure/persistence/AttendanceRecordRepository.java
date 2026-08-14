package com.theieltsspells.attendance.infrastructure.persistence;

import com.theieltsspells.attendance.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    List<AttendanceRecord> findBySessionIdInOrderBySessionId(List<UUID> sessionIds);
    Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    @Query("select record from AttendanceRecord record " +
            "join fetch record.studentRef student join fetch student.userRef profile " +
            "where record.sessionId = :sessionId order by profile.fullName")
    List<AttendanceRecord> findRosterBySessionId(@Param("sessionId") UUID sessionId);
}
