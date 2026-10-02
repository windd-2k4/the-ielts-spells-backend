package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderStatusResponse(
        String orderCode,
        OrderStatus status,
        BigDecimal amount,
        OffsetDateTime paidAt,
        String courseTitle,
        String invoiceLookupUrl,
        String invoicePdfUrl
) {}
