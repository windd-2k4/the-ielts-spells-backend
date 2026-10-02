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
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class Order {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true, length = 32)
    private String orderCode;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "invoice_required", nullable = false)
    private Boolean invoiceRequired = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "buyer_type", nullable = false)
    private InvoiceBuyerType buyerType = InvoiceBuyerType.PERSONAL;

    @Column(name = "invoice_company_name")
    private String invoiceCompanyName;

    @Column(name = "invoice_tax_code", length = 20)
    private String invoiceTaxCode;

    @Column(name = "invoice_address")
    private String invoiceAddress;

    @Column(name = "invoice_email")
    private String invoiceEmail;

    @Column(name = "pilot_approved", nullable = false)
    private Boolean pilotApproved = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
