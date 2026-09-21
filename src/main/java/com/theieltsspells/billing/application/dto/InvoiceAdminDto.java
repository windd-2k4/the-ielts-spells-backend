package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.InvoiceBuyerType;
import com.theieltsspells.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceAdminDto(
        UUID id,
        UUID orderId,
        String orderCode,
        String customerName,
        String customerEmail,
        BigDecimal amount,
        InvoiceBuyerType buyerType,
        String invoiceCompanyName,
        String invoiceTaxCode,
        String invoiceTemplate,
        String invoiceNumber,
        String cqtCode,
        String lookupCode,
        String lookupUrl,
        String pdfUrl,
        String xmlUrl,
        InvoiceStatus status,
        Integer retryCount,
        String errorLog,
        OffsetDateTime issuedAt,
        OffsetDateTime createdAt
) {}
