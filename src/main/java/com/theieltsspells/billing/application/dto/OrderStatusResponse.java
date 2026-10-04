package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.OrderStatus;
import com.theieltsspells.billing.domain.AccountActivationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderStatusResponse(
        String orderCode,
        OrderStatus status,
        AccountActivationStatus accountActivationStatus,
        BigDecimal amount,
        OffsetDateTime paidAt,
        String courseTitle,
        String invoiceLookupUrl,
        String invoicePdfUrl
) {}
