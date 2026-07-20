package com.theieltsspells.attendance.domain;

import com.theieltsspells.shared.persistence.enums.*;
import com.theieltsspells.academic.domain.ClassSession;
import com.theieltsspells.identity.domain.Profile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "attendance_imports")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class AttendanceImport {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "source_file_path")
    private String sourceFilePath;

    @Column(name = "external_meeting_id")
    private String externalMeetingId;

    @Column(name = "imported_by")
    private UUID importedBy;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private ClassSession sessionRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by", insertable = false, updatable = false)
    private Profile importedByRef;
}
