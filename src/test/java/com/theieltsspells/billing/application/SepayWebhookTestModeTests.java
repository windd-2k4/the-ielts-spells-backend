package com.theieltsspells.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.application.EnrollmentApplicationService;
import com.theieltsspells.billing.application.dto.SepayWebhookPayload;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.persistence.PaymentTransactionRepository;
import com.theieltsspells.billing.infrastructure.security.SecretEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SepayWebhookTestModeTests {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BillingSettingRepository billingSettingRepository;

    @Mock
    private AccountActivationService activationService;

    @Mock
    private ElectronicInvoiceService invoiceService;

    @Mock
    private EmailBillingNotificationService emailService;

    @Mock
    private EnrollmentApplicationService enrollmentService;

    private SepayWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new SepayWebhookService(
                transactionRepository,
                orderRepository,
                billingSettingRepository,
                activationService,
                invoiceService,
                emailService,
                enrollmentService,
                new ObjectMapper(),
                new SecretEncryptionService()
        );
    }

    @Test
    void acceptsSepaySampleAccountWithoutPersistingAnything() {
        BillingSetting setting = new BillingSetting();
        setting.setSepayWebhookSecret("test-secret");
        setting.setSepayAccountNumber("1069181004");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        Map<String, Object> result = webhookService.processTestWebhook(
                "Apikey test-secret",
                samplePayload()
        );

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("mode", "test")
                .containsEntry("status", "TEST_RECEIVED");
        verify(billingSettingRepository).findLatest();
        verifyNoInteractions(transactionRepository, orderRepository, activationService,
                invoiceService, emailService, enrollmentService);
    }

    @Test
    void stillRequiresWebhookAuthentication() {
        BillingSetting setting = new BillingSetting();
        setting.setSepayWebhookSecret("test-secret");
        when(billingSettingRepository.findLatest()).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> webhookService.processTestWebhook("Apikey wrong-secret", samplePayload()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Webhook authentication failed");
        verifyNoInteractions(transactionRepository, orderRepository, activationService,
                invoiceService, emailService, enrollmentService);
    }

    private SepayWebhookPayload samplePayload() {
        return new SepayWebhookPayload(
                999999L,
                "Vietcombank",
                "2026-11-01 12:00:00",
                "1017588888",
                null,
                "",
                "SEPAY TEST WEBHOOK",
                "in",
                "SePay test webhook",
                BigDecimal.TEN,
                "TEST-REF",
                BigDecimal.TEN
        );
    }
}
