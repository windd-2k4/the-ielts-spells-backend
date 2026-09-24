package com.theieltsspells.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
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

    @Column(name = "order_id")
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    @Column(name = "payment_transaction_id", unique = true)
    private UUID paymentTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id", insertable = false, updatable = false)
    private PaymentTransaction paymentTransaction;

    @Column(name = "product_name", nullable = false)
    private String productName = "Đóng học phí đào tạo IELTS";

    /**
     * Mã tham chiếu duy nhất cho SePay eInvoice (Format: INV-{orderCode})
     * DB Unique Constraint bảo đảm không bao giờ bị phát hành trùng
     */
    @Column(name = "reference_code", length = 64, unique = true)
    private String referenceCode;

    @Column(name = "create_tracking_code", length = 64)
    private String createTrackingCode;

    @Column(name = "issue_tracking_code", length = 64)
    private String issueTrackingCode;

    @Column(name = "provider_account_id", length = 64)
    private String providerAccountId;

    @Column(name = "template_code", length = 20)
    private String templateCode;

    @Column(name = "invoice_series", length = 20)
    private String invoiceSeries;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft = false;

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

    @Column(name = "buyer_type", length = 20)
    private String buyerType;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_legal_name")
    private String buyerLegalName;

    @Column(name = "buyer_tax_code", length = 20)
    private String buyerTaxCode;

    @Column(name = "buyer_address")
    private String buyerAddress;

    @Column(name = "buyer_email")
    private String buyerEmail;

    @Column(name = "buyer_phone")
    private String buyerPhone;

    @Column(name = "payment_method", length = 10)
    private String paymentMethod = "CK";

    @Column(name = "subtotal", precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_rate")
    private Integer taxRate;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_error_code", length = 50)
    private String providerErrorCode;

    @Column(name = "provider_error_message")
    private String providerErrorMessage;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status = InvoiceStatus.PENDING_ISSUE;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "error_log")
    private String errorLog;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_treatment", length = 30, nullable = false)
    private TaxTreatment taxTreatment = TaxTreatment.NOT_SUBJECT_TO_VAT;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", length = 30, nullable = false)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", length = 30)
    private InvoiceErrorCategory errorCategory;

    @Column(name = "first_submitted_at")
    private OffsetDateTime firstSubmittedAt;

    @Column(name = "last_status_checked_at")
    private OffsetDateTime lastStatusCheckedAt;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
