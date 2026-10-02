package com.theieltsspells.billing.presentation.admin;

import com.theieltsspells.billing.application.ElectronicInvoiceService;
import com.theieltsspells.billing.application.OrderApplicationService;
import com.theieltsspells.billing.application.SepayWebhookService;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.domain.ProductionActivationState;
import com.theieltsspells.billing.infrastructure.persistence.BillingSettingRepository;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceClient;
import com.theieltsspells.billing.infrastructure.sepay.SepayEInvoiceTokenService;
import com.theieltsspells.shared.application.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBillingSandboxSmokeTest {

    @Mock private OrderApplicationService orderService;
    @Mock private ElectronicInvoiceService invoiceService;
    @Mock private SepayWebhookService webhookService;
    @Mock private BillingSettingRepository settingsRepository;
    @Mock private SepayEInvoiceClient sepayEInvoiceClient;
    @Mock private SepayEInvoiceTokenService tokenService;

    private AdminBillingController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminBillingController(
                orderService,
                invoiceService,
                webhookService,
                settingsRepository,
                sepayEInvoiceClient,
                tokenService
        );
    }

    @Test
    void sandboxSmokeIsBlockedInProductionContext() {
        BillingSetting setting = new BillingSetting();
        setting.setActivationState(ProductionActivationState.PRODUCTION_CONFIGURED);
        when(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> controller.testEinvoiceSandboxE2e(true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SANDBOX");

        verifyNoInteractions(sepayEInvoiceClient);
    }

    @Test
    void sandboxSmokeUsesSavedSettingForTheFullLifecycle() {
        BillingSetting setting = new BillingSetting();
        setting.setActivationState(ProductionActivationState.SANDBOX);
        when(settingsRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(setting));
        when(sepayEInvoiceClient.testConnection(setting)).thenReturn(new SepayEInvoiceClient.ConnectionTestResult(
                true, "Sandbox connected", "MATBAO", "provider-1", "C26TSE", "1", null, 99
        ));
        when(sepayEInvoiceClient.createInvoice(eq(setting), any()))
                .thenReturn(new SepayEInvoiceClient.CreateResult(true, "create-track", null, null));
        when(sepayEInvoiceClient.checkCreateStatus(setting, "create-track"))
                .thenReturn(SepayEInvoiceClient.CheckStatusResult.successDraft(null, "C26TSE", null, null, null, null));
        when(sepayEInvoiceClient.getInvoiceDetail(eq(setting), any()))
                .thenReturn(
                        SepayEInvoiceClient.InvoiceDetailResult.ofDraft(null, "C26TSE", null),
                        SepayEInvoiceClient.InvoiceDetailResult.ofSuccess("1", "C26TSE", null, null, null, null)
                );
        when(sepayEInvoiceClient.issueDraft(eq(setting), any()))
                .thenReturn(new SepayEInvoiceClient.IssueDraftResult(true, "issue-track", null, null));
        when(sepayEInvoiceClient.checkIssueStatus(setting, "issue-track"))
                .thenReturn(SepayEInvoiceClient.CheckStatusResult.successIssued("1", "C26TSE", null, null, null, null));
        when(sepayEInvoiceClient.downloadInvoiceFile(setting, "issue-track", "pdf"))
                .thenReturn(new SepayEInvoiceClient.DownloadResult(true, "pdf", "invoice.pdf", "%PDF-test".getBytes(), null, null));
        when(sepayEInvoiceClient.downloadInvoiceFile(setting, "issue-track", "xml"))
                .thenReturn(new SepayEInvoiceClient.DownloadResult(true, "xml", "invoice.xml", "<invoice/>".getBytes(), null, null));

        AdminBillingController.SandboxSmokeResult result = controller.testEinvoiceSandboxE2e(true).getBody();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.steps()).extracting(AdminBillingController.SandboxSmokeStep::name)
                .containsExactly("CONNECTION", "CREATE_DRAFT", "IDEMPOTENCY", "GET_DRAFT", "ISSUE", "GET_ISSUED", "DOWNLOAD");
        verify(sepayEInvoiceClient).downloadInvoiceFile(setting, "issue-track", "pdf");
        verify(sepayEInvoiceClient).downloadInvoiceFile(setting, "issue-track", "xml");
        verify(sepayEInvoiceClient, never()).downloadInvoiceFile(eq(setting), eq("create-track"), any());
    }
}
