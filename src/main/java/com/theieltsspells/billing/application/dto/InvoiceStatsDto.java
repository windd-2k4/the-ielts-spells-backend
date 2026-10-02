package com.theieltsspells.billing.application.dto;

import java.math.BigDecimal;

public record InvoiceStatsDto(
        long totalCount,
        long issuedCount,
        long pendingCount,
        long failedCount,
        long cancelledCount,
        BigDecimal totalInvoicedAmount
) {}
