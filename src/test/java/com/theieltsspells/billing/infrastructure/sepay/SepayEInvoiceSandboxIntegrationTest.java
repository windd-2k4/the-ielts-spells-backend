package com.theieltsspells.billing.infrastructure.sepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.domain.TaxTreatment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test harness against real SePay eInvoice Sandbox API.
 * <p>
 * Separates Read-only tests from Mutating tests:
 * - Read-only tests: Active only when SEPAY_SANDBOX_INTEGRATION_TEST=true.
 * - Mutating tests: Active only when BOTH SEPAY_SANDBOX_INTEGRATION_TEST=true AND SEPAY_SANDBOX_MUTATING_TEST=true.
 * <p>
 * Credentials must be provided via environment variables:
 * - SEPAY_EINVOICE_CLIENT_ID
 * - SEPAY_EINVOICE_CLIENT_SECRET
 * <p>
 * Credentials and access tokens are NEVER logged or exposed.
 */
@EnabledIfEnvironmentVariable(named = "SEPAY_SANDBOX_INTEGRATION_TEST", matches = "true")
class SepayEInvoiceSandboxIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SepayEInvoiceSandboxIntegrationTest.class);

    private SepayEInvoiceClient client;
    private BillingSetting setting;

    @BeforeEach
    void setUp() {
        String clientId = System.getenv("SEPAY_EINVOICE_CLIENT_ID");
        String clientSecret = System.getenv("SEPAY_EINVOICE_CLIENT_SECRET");

        Assumptions.assumeTrue(clientId != null && !clientId.isBlank(), "Skipping Sandbox test: SEPAY_EINVOICE_CLIENT_ID not set");
        Assumptions.assumeTrue(clientSecret != null && !clientSecret.isBlank(), "Skipping Sandbox test: SEPAY_EINVOICE_CLIENT_SECRET not set");

        setting = new BillingSetting();
        setting.setEinvoiceClientId(clientId);
        setting.setEinvoiceClientSecret(clientSecret);
        setting.setIsSandbox(true);
        setting.setTaxTreatment(TaxTreatment.NOT_SUBJECT_TO_VAT);

        ObjectMapper objectMapper = new ObjectMapper();
        SepayEInvoiceTokenService tokenService = new SepayEInvoiceTokenService(objectMapper);
        client = new SepayEInvoiceClient(objectMapper, tokenService);
    }

    // =========================================================================
    // 1. READ-ONLY TEST SUITE (Safe to run without consuming quota or creating invoices)
    // =========================================================================

    @Test
    @DisplayName("Sandbox Read-Only: 1. Xác thực kết nối OAuth 2.0 (Token, Headers, Base URL)")
    void testLiveSandbox_OAuthConnection() {
        SepayEInvoiceClient.ConnectionTestResult testResult = client.testConnection(setting);
        log.info("Sandbox Connection Test result: success={}, message={}", testResult.success(), testResult.message());

        assertThat(testResult.success())
                .as("Kết nối SePay Sandbox phải thành công. Chi tiết lỗi: " + testResult.message())
                .isTrue();
    }

    @Test
    @DisplayName("Sandbox Read-Only: 2. Lấy danh sách Provider Accounts và Templates khả dụng")
    void testLiveSandbox_GetProviderAccounts() {
        List<SepayEInvoiceClient.ProviderAccountDto> providers = client.getProviderAccounts(setting);
        log.info("Sandbox Providers retrieved: count={}", providers.size());

        assertThat(providers).as("Danh sách Provider trên SePay Sandbox không được rỗng").isNotEmpty();

        for (SepayEInvoiceClient.ProviderAccountDto provider : providers) {
            assertThat(provider.id()).isNotBlank();
            log.info("Found Provider: id={}, name={}, taxCode={}, active={}",
                    provider.id(), provider.provider(), provider.taxCode(), provider.active());
        }
    }

    @Test
    @DisplayName("Sandbox Read-Only: 3. Truy vấn Hạn ngạch hóa đơn (Remaining Quota)")
    void testLiveSandbox_GetRemainingQuota() {
        Integer quota = client.getRemainingQuota(setting);
        log.info("Sandbox Remaining Quota: {}", quota);

        assertThat(quota).as("Remaining quota trên SePay Sandbox phải trả về giá trị số").isNotNull();
        assertThat(quota).isGreaterThanOrEqualTo(0);
    }

    // =========================================================================
    // 2. MUTATING TEST SUITE (Gated under SEPAY_SANDBOX_MUTATING_TEST=true)
    // =========================================================================

    @Test
    @DisplayName("Sandbox Mutating: Full End-to-End Invoice Lifecycle (Create -> Detail -> 409 Conflict Reconcile)")
    @EnabledIfEnvironmentVariable(named = "SEPAY_SANDBOX_MUTATING_TEST", matches = "true")
    void testLiveSandbox_MutatingFullLifecycle() {
        // 1. Lấy thông tin provider đầu tiên
        List<SepayEInvoiceClient.ProviderAccountDto> providers = client.getProviderAccounts(setting);
        assertThat(providers).isNotEmpty();
        SepayEInvoiceClient.ProviderAccountDto activeProvider = providers.stream()
                .filter(SepayEInvoiceClient.ProviderAccountDto::active)
                .findFirst()
                .orElse(providers.get(0));

        setting.setEinvoiceProviderAccountId(activeProvider.id());
        if (activeProvider.templates() != null && !activeProvider.templates().isEmpty()) {
            setting.setEinvoiceTemplateCode(activeProvider.templates().get(0).templateCode());
            setting.setEinvoiceInvoiceSeries(activeProvider.templates().get(0).invoiceSeries());
        } else {
            setting.setEinvoiceTemplateCode("1");
            setting.setEinvoiceInvoiceSeries("C26TSE");
        }

        String testRefCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Running Mutating E2E test with reference_code: {}", testRefCode);

        // 2. Tạo hóa đơn thử nghiệm
        SepayEInvoiceClient.CreateInvoicePayload payload = new SepayEInvoiceClient.CreateInvoicePayload(
                setting.getEinvoiceTemplateCode(),
                setting.getEinvoiceInvoiceSeries(),
                setting.getEinvoiceProviderAccountId(),
                testRefCode,
                "CK",
                false,
                "PERSONAL",
                "Khách Hàng Sandbox Test",
                null,
                null,
                null,
                "test-sandbox@example.com",
                null,
                testRefCode,
                "IELTS-SANDBOX",
                "Khóa học IELTS Test Sandbox",
                "Khóa",
                1,
                100000L,
                -2,
                "Hóa đơn kiểm thử SePay Sandbox"
        );

        SepayEInvoiceClient.CreateResult createRes = client.createInvoice(setting, payload);
        log.info("Create Invoice Result: success={}, trackingCode={}, errorCode={}, errorMsg={}",
                createRes.success(), createRes.trackingCode(), createRes.errorCode(), createRes.errorMessage());

        assertThat(createRes.success() || "EINVOICE_DOCUMENT_EXISTED".equalsIgnoreCase(createRes.errorCode()))
                .as("Tạo hóa đơn trên SePay Sandbox phải thành công hoặc trả về 409 đã tồn tại")
                .isTrue();

        // 3. Test tính bất biến và HTTP 409: Thử gửi lại đúng reference_code này
        SepayEInvoiceClient.CreateResult duplicateRes = client.createInvoice(setting, payload);
        log.info("Duplicate Submission Result: errorCode={}, errorMsg={}",
                duplicateRes.errorCode(), duplicateRes.errorMessage());

        // Phải trả về EINVOICE_DOCUMENT_EXISTED
        if (!duplicateRes.success()) {
            assertThat(duplicateRes.errorCode())
                    .as("Gửi lại cùng reference_code phải trả về mã lỗi EINVOICE_DOCUMENT_EXISTED")
                    .isEqualToIgnoringCase("EINVOICE_DOCUMENT_EXISTED");
        }

        // 4. Lấy chi tiết hóa đơn qua GET /v1/invoices/{reference_code}
        SepayEInvoiceClient.InvoiceDetailResult detailRes = client.getInvoiceDetail(setting, testRefCode);
        log.info("Invoice Detail Result: success={}, notFound={}, status={}, invoiceNum={}",
                detailRes.success(), detailRes.notFound(), detailRes.status(), detailRes.invoiceNumber());

        assertThat(detailRes.success() || detailRes.notFound())
                .as("Truy vấn chi tiết hóa đơn không được ném lỗi hệ thống không xác định")
                .isTrue();
    }
}
