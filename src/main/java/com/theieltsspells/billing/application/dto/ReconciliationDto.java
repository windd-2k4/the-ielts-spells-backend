package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.PaymentTransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ReconciliationDto(
        UUID id,
        String gateway,
        String sepayTransactionId,
        UUID orderId,
        String orderCode,
        BigDecimal amountIn,
        BigDecimal accumulatedAmount,
        String payerName,
        String transferContent,
        String bankBrandName,
        String accountNumber,
        PaymentTransactionStatus status,
        String reconciliationNote,
        Map<String, Object> rawPayload,
        OffsetDateTime createdAt
) {}
