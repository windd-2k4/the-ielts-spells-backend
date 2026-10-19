package com.theieltsspells.billing.presentation.admin;

import com.theieltsspells.billing.application.ElectronicInvoiceService;
import com.theieltsspells.billing.application.OrderApplicationService;
import com.theieltsspells.billing.application.SepayWebhookService;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.domain.ElectronicInvoice;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.persistence.ElectronicInvoiceRepository;
import com.theieltsspells.billing.infrastructure.persistence.OrderRepository;
import com.theieltsspells.billing.infrastructure.security.SecretEncryptionService;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceClient;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBillingPdfTests {

    @Mock private OrderApplicationService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private ElectronicInvoiceService invoiceService;
    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private SepayWebhookService webhookService;
    @Mock private BillingSettingRepository settingsRepository;
    @Mock private SepayEInvoiceClient sepayEInvoiceClient;
    @Mock private SepayEInvoiceTokenService tokenService;

    private AdminBillingController controller;
    private BillingSetting setting;

    @BeforeEach
    void setUp() {
        controller = new AdminBillingController(
                orderService,
                orderRepository,
                invoiceService,
                invoiceRepository,
                webhookService,
                settingsRepository,
                sepayEInvoiceClient,
                tokenService,
                new SecretEncryptionService()
        );
        setting = new BillingSetting();
    }

    @Test
    void downloadsByReferenceCodeWhenLegacyInvoiceHasNoTrackingCode() {
        UUID id = UUID.randomUUID();
        ElectronicInvoice invoice = invoice(id, null, null, "INV-LEGACY-001");
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoice));
        when(settingsRepository.findLatest()).thenReturn(Optional.of(setting));
        when(sepayEInvoiceClient.downloadInvoiceFile(setting, "INV-LEGACY-001", "pdf"))
                .thenReturn(new SepayEInvoiceClient.DownloadResult(true, "pdf", "invoice.pdf", content, null, null));

        ResponseEntity<byte[]> response = controller.previewInvoicePdf(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(content);
    }

    @Test
    void fallsBackFromUnknownTrackingCodeToReferenceCode() {
        UUID id = UUID.randomUUID();
        ElectronicInvoice invoice = invoice(id, "ISSUE-404", null, "INV-002");
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoice));
        when(settingsRepository.findLatest()).thenReturn(Optional.of(setting));
        when(sepayEInvoiceClient.downloadInvoiceFile(setting, "ISSUE-404", "pdf"))
                .thenReturn(new SepayEInvoiceClient.DownloadResult(false, null, null, null, "HTTP_404", "SePay trả về HTTP 404"));
        when(sepayEInvoiceClient.downloadInvoiceFile(setting, "INV-002", "pdf"))
                .thenReturn(new SepayEInvoiceClient.DownloadResult(true, "pdf", "invoice.pdf", content, null, null));

        controller.previewInvoicePdf(id);

        InOrder order = inOrder(sepayEInvoiceClient);
        order.verify(sepayEInvoiceClient).downloadInvoiceFile(setting, "ISSUE-404", "pdf");
        order.verify(sepayEInvoiceClient).downloadInvoiceFile(setting, "INV-002", "pdf");
    }

    @Test
    void reportsProviderTimeoutAsServiceUnavailableWithoutChangingInvoiceState() {
        UUID id = UUID.randomUUID();
        ElectronicInvoice invoice = invoice(id, "ISSUE-TIMEOUT", null, "INV-003");
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoice));
        when(settingsRepository.findLatest()).thenReturn(Optional.of(setting));
        when(sepayEInvoiceClient.downloadInvoiceFile(setting, "ISSUE-TIMEOUT", "pdf"))
                .thenReturn(new SepayEInvoiceClient.DownloadResult(false, null, null, null, "EXCEPTION", "request timed out"));

        assertThatThrownBy(() -> controller.previewInvoicePdf(id))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).contains("Hóa đơn vẫn đã phát hành");
                });
    }

    private static ElectronicInvoice invoice(UUID id, String issueTrackingCode, String createTrackingCode, String referenceCode) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(id);
        invoice.setInvoiceNumber("6");
        invoice.setIssueTrackingCode(issueTrackingCode);
        invoice.setCreateTrackingCode(createTrackingCode);
        invoice.setReferenceCode(referenceCode);
        return invoice;
    }
}
