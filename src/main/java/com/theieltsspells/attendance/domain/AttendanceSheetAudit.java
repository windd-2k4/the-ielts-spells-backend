package com.theieltsspells.attendance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_sheet_audits")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class AttendanceSheetAudit {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "sheet_id", nullable = false)
    private UUID sheetId;
    @Column(nullable = false)
    private String action;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    private String reason;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
