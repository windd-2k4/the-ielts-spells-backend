package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.InvoiceBuyerType;
import com.theieltsspells.billing.domain.InvoiceStatus;
import com.theieltsspells.billing.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderAdminDto(
        UUID id,
        String orderCode,
        UUID courseId,
        String courseTitle,
        UUID userId,
        String customerName,
        String customerEmail,
        String customerPhone,
        BigDecimal amount,
        OrderStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime paidAt,
        Boolean invoiceRequired,
        InvoiceBuyerType buyerType,
        String invoiceCompanyName,
        String invoiceTaxCode,
        String invoiceAddress,
        String invoiceEmail,
        InvoiceStatus invoiceStatus,
        String invoiceNumber,
        String invoiceTemplate,
        String cqtCode,
        String lookupUrl,
        String pdfUrl,
        OffsetDateTime createdAt
) {}
