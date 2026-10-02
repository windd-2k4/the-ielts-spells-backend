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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class PaymentTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "gateway", nullable = false, length = 20)
    private String gateway = "SEPAY";

    @Column(name = "sepay_transaction_id", nullable = false, unique = true, length = 64)
    private String sepayTransactionId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_code", length = 32)
    private String orderCode;

    @Column(name = "amount_in", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountIn;

    @Column(name = "accumulated_amount", precision = 12, scale = 2)
    private BigDecimal accumulatedAmount;

    @Column(name = "transfer_content")
    private String transferContent;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "bank_brand_name", length = 50)
    private String bankBrandName;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private PaymentTransactionStatus status = PaymentTransactionStatus.SUCCESS;

    @Column(name = "reconciliation_note")
    private String reconciliationNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private Map<String, Object> rawPayload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
