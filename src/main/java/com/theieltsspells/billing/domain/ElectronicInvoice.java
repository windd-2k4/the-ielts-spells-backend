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
import java.util.UUID;

@Entity
@Table(name = "electronic_invoices")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class ElectronicInvoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    @Column(name = "invoice_template", length = 20)
    private String invoiceTemplate;

    @Column(name = "invoice_number", length = 20)
    private String invoiceNumber;

    @Column(name = "cqt_code", length = 100)
    private String cqtCode;

    @Column(name = "lookup_code", length = 64)
    private String lookupCode;

    @Column(name = "lookup_url")
    private String lookupUrl;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "xml_url")
    private String xmlUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status = InvoiceStatus.PENDING_ISSUE;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_log")
    private String errorLog;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
