package com.theieltsspells.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.UuidGenerator;

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

    @Column(name = "seller_name")
    private String sellerName = "HỘ KINH DOANH THE IELTS SPELLS";

    @Column(name = "seller_tax_code", length = 20)
    private String sellerTaxCode;

    @Column(name = "seller_address")
    private String sellerAddress;

    @Column(name = "is_sandbox", nullable = false)
    private Boolean isSandbox = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
