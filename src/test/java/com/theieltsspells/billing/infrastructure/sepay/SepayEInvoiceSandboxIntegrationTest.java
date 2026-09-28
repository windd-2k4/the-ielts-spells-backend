package com.theieltsspells.billing.infrastructure.sepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.domain.TaxTreatment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
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
 * - SEPAY_EINVOICE_SANDBOX_CLIENT_ID (or legacy SEPAY_EINVOICE_CLIENT_ID)
 * - SEPAY_EINVOICE_SANDBOX_CLIENT_SECRET (or legacy SEPAY_EINVOICE_CLIENT_SECRET)
 * <p>
 * Credentials and access tokens are NEVER logged or exposed.
 */
class SepayEInvoiceSandboxIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SepayEInvoiceSandboxIntegrationTest.class);

    private SepayEInvoiceClient client;
    private BillingSetting setting;
    private Properties dotenv;

    @BeforeEach
    void setUp() {
        dotenv = loadDotenv();
        Assumptions.assumeTrue(Boolean.parseBoolean(config("SEPAY_SANDBOX_INTEGRATION_TEST")),
                "Skipping live Sandbox tests: SEPAY_SANDBOX_INTEGRATION_TEST is not true");

        String clientId = firstConfigured("SEPAY_EINVOICE_SANDBOX_CLIENT_ID", "SEPAY_EINVOICE_CLIENT_ID");
        String clientSecret = firstConfigured("SEPAY_EINVOICE_SANDBOX_CLIENT_SECRET", "SEPAY_EINVOICE_CLIENT_SECRET");

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
    void testLiveSandbox_MutatingFullLifecycle() {
        Assumptions.assumeTrue(Boolean.parseBoolean(config("SEPAY_SANDBOX_MUTATING_TEST")),
                "Skipping mutating Sandbox test: SEPAY_SANDBOX_MUTATING_TEST is not true");

        // 1. Lấy thông tin provider đầu tiên
        List<SepayEInvoiceClient.ProviderAccountDto> providers = client.getProviderAccounts(setting);
        assertThat(providers).isNotEmpty();
        SepayEInvoiceClient.ProviderAccountDto activeProvider = providers.stream()
                .filter(SepayEInvoiceClient.ProviderAccountDto::active)
                .filter(provider -> provider.templates() != null && !provider.templates().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sandbox chưa có Provider active kèm mẫu số/ký hiệu"));

        setting.setEinvoiceProviderAccountId(activeProvider.id());
        if (activeProvider.templates() != null && !activeProvider.templates().isEmpty()) {
            setting.setEinvoiceTemplateCode(activeProvider.templates().get(0).templateCode());
            setting.setEinvoiceInvoiceSeries(activeProvider.templates().get(0).invoiceSeries());
        }

        String testRefCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Running Mutating E2E test with reference_code: {}", testRefCode);

        // 2. Tạo hóa đơn nháp thử nghiệm
        SepayEInvoiceClient.CreateInvoicePayload payload = new SepayEInvoiceClient.CreateInvoicePayload(
                setting.getEinvoiceTemplateCode(),
                setting.getEinvoiceInvoiceSeries(),
                setting.getEinvoiceProviderAccountId(),
                testRefCode,
                "CK",
                true,
                "PERSONAL",
                "Khách Hàng Sandbox Test",
                null,
                null,
                null,
                "test-sandbox@example.com",
                null,
                testRefCode,
                "IELTS-SANDBOX",
                "Đóng học phí đào tạo IELTS",
                "Khóa",
                1,
                100000L,
                -2,
                "Hóa đơn kiểm thử SePay Sandbox"
        );

        SepayEInvoiceClient.CreateResult createRes = client.createInvoice(setting, payload);
        log.info("Create Draft Result: success={}, trackingCode={}, errorCode={}",
                createRes.success(), createRes.trackingCode(), createRes.errorCode());

        assertThat(createRes.success()).as("Tạo hóa đơn nháp trên SePay Sandbox phải thành công").isTrue();
        assertThat(createRes.trackingCode()).isNotBlank();

        SepayEInvoiceClient.CheckStatusResult createStatus = pollCreate(createRes.trackingCode());
        assertThat(createStatus.success()).as("Tạo hóa đơn nháp phải hoàn tất thành công").isTrue();
        assertThat(createStatus.isDraft()).as("Kết quả create phải là hóa đơn nháp").isTrue();

        // 3. Test tính bất biến và HTTP 409: Thử gửi lại đúng reference_code này
        SepayEInvoiceClient.CreateResult duplicateRes = client.createInvoice(setting, payload);
        log.info("Duplicate Submission Result: success={}, errorCode={}",
                duplicateRes.success(), duplicateRes.errorCode());

        // Phải trả về EINVOICE_DOCUMENT_EXISTED
        if (!duplicateRes.success()) {
            assertThat(duplicateRes.errorCode())
                    .as("Gửi lại cùng reference_code phải trả về mã lỗi EINVOICE_DOCUMENT_EXISTED")
                    .isEqualToIgnoringCase("EINVOICE_DOCUMENT_EXISTED");
        }

        // 4. Lấy chi tiết hóa đơn nháp qua GET /v1/invoices/{reference_code}
        SepayEInvoiceClient.InvoiceDetailResult detailRes = client.getInvoiceDetail(setting, testRefCode);
        log.info("Invoice Detail Result: success={}, notFound={}, status={}, invoiceNum={}",
                detailRes.success(), detailRes.notFound(), detailRes.status(), detailRes.invoiceNumber());

        assertThat(detailRes.success()).as("Phải truy vấn được hóa đơn nháp vừa tạo").isTrue();

        // 5. Phát hành hóa đơn nháp và poll đến trạng thái cuối
        SepayEInvoiceClient.IssueDraftResult issueRes = client.issueDraft(setting, testRefCode);
        log.info("Issue Draft Result: success={}, trackingCode={}, errorCode={}",
                issueRes.success(), issueRes.trackingCode(), issueRes.errorCode());
        assertThat(issueRes.success()).as("Lệnh phát hành hóa đơn nháp phải được SePay tiếp nhận").isTrue();
        assertThat(issueRes.trackingCode()).isNotBlank();

        SepayEInvoiceClient.CheckStatusResult issueStatus = pollIssue(issueRes.trackingCode());
        assertThat(issueStatus.success()).as("Phát hành hóa đơn phải hoàn tất thành công").isTrue();
        assertThat(issueStatus.isDraft()).isFalse();

        // 6. Đồng bộ detail và kiểm tra tải được cả PDF/XML bằng Bearer token
        SepayEInvoiceClient.InvoiceDetailResult issuedDetail = client.getInvoiceDetail(setting, testRefCode);
        assertThat(issuedDetail.success()).isTrue();

        SepayEInvoiceClient.DownloadResult pdf = downloadWithTrackingFallback(
                issueRes.trackingCode(), createRes.trackingCode(), "pdf");
        assertThat(pdf.success()).as("Tải PDF Sandbox phải thành công").isTrue();
        assertThat(pdf.content()).isNotEmpty();
        assertThat(new String(pdf.content(), 0, Math.min(4, pdf.content().length), StandardCharsets.US_ASCII))
                .startsWith("%PDF");

        SepayEInvoiceClient.DownloadResult xml = downloadWithTrackingFallback(
                issueRes.trackingCode(), createRes.trackingCode(), "xml");
        assertThat(xml.success()).as("Tải XML Sandbox phải thành công").isTrue();
        assertThat(xml.content()).isNotEmpty();
    }

    private SepayEInvoiceClient.CheckStatusResult pollCreate(String trackingCode) {
        SepayEInvoiceClient.CheckStatusResult result = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            result = client.checkCreateStatus(setting, trackingCode);
            if (!result.isPending()) return result;
            sleepTwoSeconds();
        }
        throw new AssertionError("Timeout khi chờ SePay Sandbox tạo hóa đơn");
    }

    private SepayEInvoiceClient.CheckStatusResult pollIssue(String trackingCode) {
        SepayEInvoiceClient.CheckStatusResult result = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            result = client.checkIssueStatus(setting, trackingCode);
            if (!result.isPending()) return result;
            sleepTwoSeconds();
        }
        throw new AssertionError("Timeout khi chờ SePay Sandbox phát hành hóa đơn");
    }

    private SepayEInvoiceClient.DownloadResult downloadWithTrackingFallback(
            String primaryTrackingCode,
            String fallbackTrackingCode,
            String type
    ) {
        SepayEInvoiceClient.DownloadResult result = client.downloadInvoiceFile(setting, primaryTrackingCode, type);
        return result.success() ? result : client.downloadInvoiceFile(setting, fallbackTrackingCode, type);
    }

    private void sleepTwoSeconds() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Sandbox smoke test bị ngắt", ex);
        }
    }

    private String firstConfigured(String preferred, String fallback) {
        String value = config(preferred);
        return value != null && !value.isBlank() ? value : config(fallback);
    }

    private String config(String key) {
        String environmentValue = System.getenv(key);
        if (environmentValue != null && !environmentValue.isBlank()) return environmentValue.trim();
        String propertyValue = System.getProperty(key);
        if (propertyValue != null && !propertyValue.isBlank()) return propertyValue.trim();
        String dotenvValue = dotenv.getProperty(key);
        return dotenvValue != null ? dotenvValue.trim() : null;
    }

    private Properties loadDotenv() {
        Properties properties = new Properties();
        Path path = Path.of(".env");
        if (!Files.isRegularFile(path)) return properties;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể đọc file .env cho Sandbox smoke test", ex);
        }
    }
}
