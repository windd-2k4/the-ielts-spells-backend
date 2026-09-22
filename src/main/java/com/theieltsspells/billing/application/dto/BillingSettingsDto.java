package com.theieltsspells.billing.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BillingSettingsDto(
        String sepayApiKey,
        String sepayWebhookSecret,
        String sepayAccountNumber,
        String sepayBankName,
        String einvoiceApiToken,
        String einvoiceClientId,
        String einvoiceClientSecret,
        String einvoiceProviderAccountId,
        String einvoiceInvoiceSeries,
        String einvoiceTemplateCode,
        BigDecimal einvoiceTaxRate,
        String sellerName,
        String sellerTaxCode,
        String sellerAddress,
        Boolean isSandbox,
        OffsetDateTime updatedAt
) {}
