package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.InvoiceBuyerType;
import com.theieltsspells.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceAdminDto(
        UUID id,
        UUID orderId,
        UUID paymentTransactionId,
        String orderCode,
        String referenceCode,
        String productName,
        String customerName,
        String customerEmail,
        BigDecimal amount,
        InvoiceBuyerType buyerType,
        String invoiceCompanyName,
        String invoiceTaxCode,
        String invoiceTemplate,
        String invoiceSeries,
        String invoiceNumber,
        String cqtCode,
        String lookupCode,
        String lookupUrl,
        String pdfUrl,
        String xmlUrl,
        InvoiceStatus status,
        Boolean isDraft,
        Integer retryCount,
        String createTrackingCode,
        String issueTrackingCode,
        String provider,
        String errorLog,
        OffsetDateTime issuedAt,
        OffsetDateTime createdAt,
        OffsetDateTime nextRetryAt,
        com.theieltsspells.billing.domain.ReconciliationStatus reconciliationStatus,
        com.theieltsspells.billing.domain.TaxTreatment taxTreatment,
        com.theieltsspells.billing.domain.InvoiceErrorCategory errorCategory
) {}
