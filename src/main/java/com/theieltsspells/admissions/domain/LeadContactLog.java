package com.theieltsspells.admissions.domain;

import com.theieltsspells.shared.persistence.enums.*;
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
@Table(name = "lead_contact_logs")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class LeadContactLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "contacted_by")
    private UUID contactedBy;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "notes")
    private String notes;

    @Column(name = "contacted_at", nullable = false)
    private OffsetDateTime contactedAt;

    @Column(name = "next_contact_at")
    private OffsetDateTime nextContactAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", insertable = false, updatable = false)
    private Lead leadRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contacted_by", insertable = false, updatable = false)
    private Profile contactedByRef;
}
