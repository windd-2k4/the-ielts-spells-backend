package com.theieltsspells.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persistent record of a controlled Pilot execution against an order.
 * Verifies all 8 critical operational milestones before Production activation.
 */
@Entity
@Table(name = "billing_pilot_executions")
@Getter
@Setter
@NoArgsConstructor
public class PilotExecution {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_code", nullable = false)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PilotResultStatus status = PilotResultStatus.NOT_STARTED;

    @Column(name = "order_paid", nullable = false)
    private boolean orderPaid;

    @Column(name = "payment_success", nullable = false)
    private boolean paymentSuccess;

    @Column(name = "payment_transaction_id")
    private String paymentTransactionId;

    @Column(name = "enrollment_active", nullable = false)
    private boolean enrollmentActive;

    @Column(name = "invoice_issued", nullable = false)
    private boolean invoiceIssued;

    @Column(name = "invoice_reference_code")
    private String invoiceReferenceCode;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_reconciled", nullable = false)
    private boolean invoiceReconciled;

    @Column(name = "pdf_status")
    private String pdfStatus;

    @Column(name = "xml_status")
    private String xmlStatus;

    @Column(name = "email_status")
    private String emailStatus;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt;

    @Column(name = "evaluated_by")
    private String evaluatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
