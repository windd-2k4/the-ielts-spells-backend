package com.theieltsspells.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_settings")
@Getter
@Setter
@NoArgsConstructor
@DynamicInsert
public class BillingSetting {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(name = "sepay_api_key")
    private String sepayApiKey;

    @Column(name = "sepay_webhook_secret")
    private String sepayWebhookSecret;

    @Column(name = "sepay_account_number")
    private String sepayAccountNumber;

    @Column(name = "sepay_bank_name")
    private String sepayBankName;

    @Column(name = "einvoice_api_token")
    private String einvoiceApiToken;

    @Column(name = "einvoice_client_id")
    private String einvoiceClientId;

    @Column(name = "einvoice_client_secret")
    private String einvoiceClientSecret;

    @Column(name = "einvoice_provider_account_id")
    private String einvoiceProviderAccountId;

    @Column(name = "einvoice_invoice_series", length = 20)
    private String einvoiceInvoiceSeries = "C26TSE";

    @Column(name = "einvoice_template_code", length = 20)
    private String einvoiceTemplateCode = "1";

    @Column(name = "einvoice_tax_rate", precision = 4, scale = 2)
    private BigDecimal einvoiceTaxRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_treatment", length = 30)
    private TaxTreatment taxTreatment;

    @Column(name = "tax_configuration_confirmed")
    private Boolean taxConfigurationConfirmed = false;

    @Column(name = "tax_configuration_confirmed_at")
    private OffsetDateTime taxConfigurationConfirmedAt;

    @Column(name = "tax_configuration_confirmed_by")
    private String taxConfigurationConfirmedBy;

    @Column(name = "last_pilot_execution_id")
    private UUID lastPilotExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pilot_status", length = 50)
    private PilotResultStatus pilotStatus = PilotResultStatus.NOT_STARTED;

    @Column(name = "invoice_type", length = 20)
    private String invoiceType = "VAT";

    @Column(name = "seller_name")
    private String sellerName = "HỘ KINH DOANH THE IELTS SPELLS";

    @Column(name = "seller_tax_code", length = 20)
    private String sellerTaxCode;

    @Column(name = "seller_address")
    private String sellerAddress;

    @Column(name = "is_sandbox", nullable = false)
    private Boolean isSandbox = true;

    @Column(name = "tax_authority_approved_date", length = 20)
    private String taxAuthorityApprovedDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "available_templates", columnDefinition = "jsonb")
    private String availableTemplates = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "activation_state", length = 30, nullable = false)
    private ProductionActivationState activationState = ProductionActivationState.SANDBOX;

    @Column(name = "auto_invoice_enabled", nullable = false)
    private Boolean autoInvoiceEnabled = true;

    @Column(name = "prod_client_id")
    private String prodClientId;

    @Column(name = "prod_client_secret")
    private String prodClientSecret;

    @Column(name = "prod_provider_account_id")
    private String prodProviderAccountId;

    @Column(name = "prod_invoice_series", length = 20)
    private String prodInvoiceSeries;

    @Column(name = "prod_template_code", length = 20)
    private String prodTemplateCode;

    @Column(name = "prod_tax_authority_approved_date", length = 20)
    private String prodTaxAuthorityApprovedDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pilot_order_allowlist", columnDefinition = "jsonb")
    private String pilotOrderAllowlist = "[]";

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean isProductionContext() {
        if (activationState != null) {
            return activationState.isProductionContext();
        }
        return Boolean.FALSE.equals(isSandbox);
    }

    public String getActiveClientId() {
        return isProductionContext() && prodClientId != null && !prodClientId.isBlank()
                ? prodClientId : einvoiceClientId;
    }

    public String getActiveClientSecret() {
        return isProductionContext() && prodClientSecret != null && !prodClientSecret.isBlank()
                ? prodClientSecret : einvoiceClientSecret;
    }

    public String getActiveProviderAccountId() {
        return isProductionContext() && prodProviderAccountId != null && !prodProviderAccountId.isBlank()
                ? prodProviderAccountId : einvoiceProviderAccountId;
    }

    public String getActiveInvoiceSeries() {
        return isProductionContext() && prodInvoiceSeries != null && !prodInvoiceSeries.isBlank()
                ? prodInvoiceSeries : einvoiceInvoiceSeries;
    }

    public String getActiveTemplateCode() {
        return isProductionContext() && prodTemplateCode != null && !prodTemplateCode.isBlank()
                ? prodTemplateCode : einvoiceTemplateCode;
    }
}
