package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        String orderCode,
        UUID courseId,
        String courseTitle,
        BigDecimal amount,
        OrderStatus status,
        String qrCodeUrl,
        String accountNumber,
        String bankName,
        String accountName,
        String transferContent,
        OffsetDateTime expiresAt
) {}
