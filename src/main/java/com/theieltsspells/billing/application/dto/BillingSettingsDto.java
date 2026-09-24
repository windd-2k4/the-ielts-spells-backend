package com.theieltsspells.billing.application.dto;

import com.theieltsspells.billing.domain.PilotResultStatus;
import com.theieltsspells.billing.domain.ProductionActivationState;
import com.theieltsspells.billing.domain.TaxTreatment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingSettingsDto(
        // Payment Gateway
        String sepayApiKey,
        Boolean sepayApiKeyConfigured,
        String maskedSepayApiKey,
        String sepayWebhookSecret,
        Boolean sepayWebhookSecretConfigured,
        String sepayAccountNumber,
        String maskedSepayAccountNumber,
        String sepayBankName,

        // Sandbox eInvoice
        String einvoiceApiToken,
        String einvoiceClientId,
        String maskedEinvoiceClientId,
        String einvoiceClientSecret,
        Boolean einvoiceClientSecretConfigured,
        String einvoiceProviderAccountId,
        String einvoiceInvoiceSeries,
        String einvoiceTemplateCode,
        BigDecimal einvoiceTaxRate,
        String taxAuthorityApprovedDate,

        // Production eInvoice
        String prodClientId,
        String maskedProdClientId,
        String prodClientSecret,
        Boolean prodClientSecretConfigured,
        String prodProviderAccountId,
        String prodInvoiceSeries,
        String prodTemplateCode,
        String prodTaxAuthorityApprovedDate,

        // Activation & Pilot Control
        ProductionActivationState activationState,
        Boolean autoInvoiceEnabled,
        String pilotOrderAllowlist,
        PilotResultStatus pilotStatus,
        UUID lastPilotExecutionId,

        // Organization Legal Info & Tax
        String sellerName,
        String sellerTaxCode,
        String sellerAddress,
        Boolean isSandbox,
        String availableTemplates,
        TaxTreatment taxTreatment,
        Boolean taxConfigurationConfirmed,
        OffsetDateTime taxConfigurationConfirmedAt,
        String taxConfigurationConfirmedBy,
        String invoiceType,
        OffsetDateTime updatedAt
) {
    public BillingSettingsDto(
            String sepayApiKey, Boolean sepayApiKeyConfigured, String maskedSepayApiKey,
            String sepayWebhookSecret, Boolean sepayWebhookSecretConfigured,
            String sepayAccountNumber, String maskedSepayAccountNumber, String sepayBankName,
            String einvoiceApiToken, String einvoiceClientId, String maskedEinvoiceClientId,
            String einvoiceClientSecret, Boolean einvoiceClientSecretConfigured,
            String einvoiceProviderAccountId, String einvoiceInvoiceSeries, String einvoiceTemplateCode,
            BigDecimal einvoiceTaxRate, String taxAuthorityApprovedDate,
            String prodClientId, String maskedProdClientId,
            String prodClientSecret, Boolean prodClientSecretConfigured,
            String prodProviderAccountId, String prodInvoiceSeries, String prodTemplateCode,
            String prodTaxAuthorityApprovedDate,
            ProductionActivationState activationState, Boolean autoInvoiceEnabled, String pilotOrderAllowlist,
            String sellerName, String sellerTaxCode, String sellerAddress,
            Boolean isSandbox, String availableTemplates, TaxTreatment taxTreatment,
            String invoiceType, OffsetDateTime updatedAt
    ) {
        this(
                sepayApiKey, sepayApiKeyConfigured, maskedSepayApiKey,
                sepayWebhookSecret, sepayWebhookSecretConfigured,
                sepayAccountNumber, maskedSepayAccountNumber, sepayBankName,
                einvoiceApiToken, einvoiceClientId, maskedEinvoiceClientId,
                einvoiceClientSecret, einvoiceClientSecretConfigured,
                einvoiceProviderAccountId, einvoiceInvoiceSeries, einvoiceTemplateCode,
                einvoiceTaxRate, taxAuthorityApprovedDate,
                prodClientId, maskedProdClientId,
                prodClientSecret, prodClientSecretConfigured,
                prodProviderAccountId, prodInvoiceSeries, prodTemplateCode,
                prodTaxAuthorityApprovedDate,
                activationState, autoInvoiceEnabled, pilotOrderAllowlist,
                null, null,
                sellerName, sellerTaxCode, sellerAddress,
                isSandbox, availableTemplates, taxTreatment,
                false, null, null,
                invoiceType, updatedAt
        );
    }
}
