package com.theieltsspells.billing.presentation.admin;

import com.theieltsspells.billing.application.ElectronicInvoiceService;
import com.theieltsspells.billing.application.OrderApplicationService;
import com.theieltsspells.billing.application.SepayWebhookService;
import com.theieltsspells.billing.application.dto.BillingSettingsDto;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.domain.ProductionActivationState;
import com.theieltsspells.billing.domain.TaxTreatment;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.security.SecretEncryptionService;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceClient;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingSecuritySerializationTests {

    @Mock
    private OrderApplicationService orderService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ElectronicInvoiceService invoiceService;
    @Mock
    private ElectronicInvoiceRepository invoiceRepository;
    @Mock
    private SepayWebhookService webhookService;
    @Mock
    private BillingSettingRepository settingsRepository;
    @Mock
    private SepayEInvoiceClient sepayEInvoiceClient;
    @Mock
    private SepayEInvoiceTokenService tokenService;

    private SecretEncryptionService encryptionService;
    private AdminBillingController controller;

    @BeforeEach
    void setUp() {
        encryptionService = new SecretEncryptionService();
        controller = new AdminBillingController(
                orderService,
                orderRepository,
                invoiceService,
                invoiceRepository,
                webhookService,
                settingsRepository,
                sepayEInvoiceClient,
                tokenService,
                encryptionService
        );
    }

    @Test
    @DisplayName("1. GET /settings NEVER returns plaintext secrets, only returns configured flags and masked values")
    void testGetSettings_NeverExposesPlaintextSecrets() {
        BillingSetting setting = new BillingSetting();
        setting.setSepayApiKey("MY_SECRET_SEPAY_API_KEY");
        setting.setSepayWebhookSecret(encryptionService.encrypt("MY_RAW_WEBHOOK_SECRET"));
        setting.setSepayAccountNumber("0987654321");
        setting.setSepayBankName("MBBank");
        setting.setEinvoiceClientId("EINV-TEST-EB0HM0MMQW9LMZHP");
        setting.setEinvoiceClientSecret(encryptionService.encrypt("SANDBOX_SECRET_KEY"));
        setting.setProdClientId("PROD-REAL-EB0HM0MMQW9LMZHP");
        setting.setProdClientSecret(encryptionService.encrypt("PROD_SECRET_KEY"));
        setting.setActivationState(ProductionActivationState.SANDBOX);
        setting.setAutoInvoiceEnabled(true);

        when(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));

        ResponseEntity<BillingSettingsDto> response = controller.getSettings();
        BillingSettingsDto dto = response.getBody();

        assertThat(dto).isNotNull();

        // STRICT SECURITY ASSERTIONS: Plaintext secret fields MUST be null
        assertThat(dto.sepayApiKey()).isNull();
        assertThat(dto.sepayWebhookSecret()).isNull();
        assertThat(dto.einvoiceClientSecret()).isNull();
        assertThat(dto.prodClientSecret()).isNull();

        // Configured boolean flags MUST be true
        assertThat(dto.sepayApiKeyConfigured()).isTrue();
        assertThat(dto.sepayWebhookSecretConfigured()).isTrue();
        assertThat(dto.einvoiceClientSecretConfigured()).isTrue();
        assertThat(dto.prodClientSecretConfigured()).isTrue();

        // Masked representations
        assertThat(dto.maskedSepayAccountNumber()).isEqualTo("******4321");
        assertThat(dto.maskedEinvoiceClientId()).contains("****");
        assertThat(dto.maskedProdClientId()).contains("****");
        assertThat(dto.maskedSepayApiKey()).contains("****");
    }

    @Test
    @DisplayName("2. PUT /settings with placeholder '********' preserves existing encrypted secret")
    void testUpdateSettings_PlaceholderDoesNotOverwriteSecret() {
        String existingEncryptedSecret = encryptionService.encrypt("original-precious-secret-123");

        BillingSetting existingSetting = new BillingSetting();
        existingSetting.setEinvoiceClientSecret(existingEncryptedSecret);
        existingSetting.setProdClientSecret(existingEncryptedSecret);
        existingSetting.setSepayWebhookSecret(existingEncryptedSecret);

        when(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existingSetting));
        when(settingsRepository.save(any(BillingSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BillingSettingsDto updateRequest = new BillingSettingsDto(
                null, true, null,
                "********", true, // placeholder for webhook secret
                "0987654321", null, "MBBank",
                null, "EINV-TEST-EB0HM0MMQW9LMZHP", null,
                "••••••••", true, // placeholder for sandbox client secret
                "prov-1", "C26TSE", "1", null, null,
                "PROD-TEST", null,
                "********", true, // placeholder for prod client secret
                null, null, null, null,
                ProductionActivationState.SANDBOX, true, "[]",
                "CÔNG TY TEST", "0123456789", "Hà Nội", true, "[]",
                TaxTreatment.NOT_SUBJECT_TO_VAT, "VAT", null
        );

        ResponseEntity<BillingSettingsDto> response = controller.updateSettings(updateRequest);
        BillingSettingsDto result = response.getBody();

        assertThat(result).isNotNull();
        // The setting object in memory must still have the original encrypted secret
        assertThat(existingSetting.getEinvoiceClientSecret()).isEqualTo(existingEncryptedSecret);
        assertThat(existingSetting.getProdClientSecret()).isEqualTo(existingEncryptedSecret);
        assertThat(existingSetting.getSepayWebhookSecret()).isEqualTo(existingEncryptedSecret);
    }

    @Test
    @DisplayName("3. PUT /settings with fresh secret encrypts before saving to database")
    void testUpdateSettings_FreshSecretIsEncrypted() {
        BillingSetting existingSetting = new BillingSetting();

        when(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existingSetting));
        when(settingsRepository.save(any(BillingSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String newPlainSecret = "brand-new-sepay-secret-9999";
        BillingSettingsDto updateRequest = new BillingSettingsDto(
                null, false, null,
                newPlainSecret, false,
                "0987654321", null, "MBBank",
                null, "EINV-TEST-EB0HM0MMQW9LMZHP", null,
                newPlainSecret, false,
                "prov-1", "C26TSE", "1", null, null,
                null, null,
                null, false,
                null, null, null, null,
                ProductionActivationState.SANDBOX, true, "[]",
                "CÔNG TY TEST", "0123456789", "Hà Nội", true, "[]",
                TaxTreatment.NOT_SUBJECT_TO_VAT, "VAT", null
        );

        controller.updateSettings(updateRequest);

        // Verify that stored secrets start with enc:v1: and decrypt to the new plaintext
        assertThat(existingSetting.getSepayWebhookSecret()).startsWith("enc:v1:");
        assertThat(encryptionService.decrypt(existingSetting.getSepayWebhookSecret())).isEqualTo(newPlainSecret);

        assertThat(existingSetting.getEinvoiceClientSecret()).startsWith("enc:v1:");
        assertThat(encryptionService.decrypt(existingSetting.getEinvoiceClientSecret())).isEqualTo(newPlainSecret);
    }
}
