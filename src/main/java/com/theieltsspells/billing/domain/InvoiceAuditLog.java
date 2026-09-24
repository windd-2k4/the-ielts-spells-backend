package com.theieltsspells.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "invoice_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class InvoiceAuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "event", length = 50, nullable = false)
    private String event;

    @Column(name = "actor", length = 100, nullable = false)
    private String actor = "SYSTEM";

    @Column(name = "message")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
