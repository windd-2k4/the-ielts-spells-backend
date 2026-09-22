package com.theieltsspells.billing.infrastructure.sepay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.academic.domain.Course;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.domain.InvoiceBuyerType;
import com.theieltsspells.billing.domain.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SepayEInvoiceClient {

    private static final String SANDBOX_BASE_URL = "https://einvoice-api-sandbox.sepay.vn";
    private static final String PRODUCTION_BASE_URL = "https://einvoice-api.sepay.vn";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // In-memory token cache
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;
    private volatile String cachedKeyFingerprint;

    public record IssueResult(
            boolean success,
            String invoiceNumber,
            String invoiceSeries,
            String cqtCode,
            String lookupCode,
            String pdfUrl,
            String xmlUrl,
            String errorMessage
    ) {}

    public record ConnectionTestResult(
            boolean success,
            String message,
            String providerName,
            String invoiceSeries,
            String templateCode,
            Integer remainingQuota
    ) {}

    private String getBaseUrl(BillingSetting settings) {
        Boolean isSandbox = settings.getIsSandbox();
        return (isSandbox == null || isSandbox) ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;
    }

    /**
     * Lấy Bearer Token xác thực SePay eInvoice (Có cache trong bộ nhớ đến khi hết hạn)
     */
    public synchronized String getAccessToken(BillingSetting settings) {
        String clientId = settings.getEinvoiceClientId();
        String clientSecret = settings.getEinvoiceClientSecret();

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình SePay eInvoice Client ID hoặc Client Secret");
        }

        String fingerprint = clientId + ":" + getBaseUrl(settings);
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry) && fingerprint.equals(cachedKeyFingerprint)) {
            return cachedToken;
        }

        try {
            String credentials = clientId.trim() + ":" + clientSecret.trim();
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            String url = getBaseUrl(settings) + "/v1/token";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", basicAuth)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Lỗi xác thực SePay eInvoice token (HTTP {}): {}", response.statusCode(), response.body());
                throw new IllegalStateException("Xác thực SePay eInvoice thất bại: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String errMsg = root.path("error").path("message").asText("Lỗi tạo token xác thực");
                throw new IllegalStateException("SePay eInvoice từ chối token: " + errMsg);
            }

            String token = root.path("data").path("access_token").asText();
            int expiresIn = root.path("data").path("expires_in").asInt(86400);

            this.cachedToken = token;
            this.tokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 300)); // Trừ 5 phút an toàn
            this.cachedKeyFingerprint = fingerprint;

            log.info("Đã làm mới SePay eInvoice Token thành công (Hết hạn trong {} giây)", expiresIn);
            return token;

        } catch (Exception ex) {
            log.error("Không thể kết nối đến SePay eInvoice để lấy token: {}", ex.getMessage());
            throw new RuntimeException("Lỗi kết nối SePay eInvoice Token: " + ex.getMessage(), ex);
        }
    }

    /**
     * Kiểm tra trạng thái kết nối và nhà cung cấp trên SePay eInvoice
     */
    public ConnectionTestResult testConnection(BillingSetting settings) {
        try {
            String token = getAccessToken(settings);
            String url = getBaseUrl(settings) + "/v1/provider-accounts?page=1&per_page=20";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new ConnectionTestResult(false, "Không thể lấy danh sách Provider (HTTP " + response.statusCode() + ")", null, null, null, null);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("data").path("items");
            if (!items.isArray() || items.isEmpty()) {
                return new ConnectionTestResult(false, "Không tìm thấy Provider Account nào đang hoạt động trên SePay", null, null, null, null);
            }

            JsonNode activeProvider = items.get(0);
            for (JsonNode item : items) {
                if (item.path("active").asBoolean(false)) {
                    activeProvider = item;
                    break;
                }
            }

            String providerId = activeProvider.path("id").asText();
            String providerName = activeProvider.path("provider").asText("matbao");

            // Lấy chi tiết provider để biết series và template
            String detailUrl = getBaseUrl(settings) + "/v1/provider-accounts/" + providerId;
            HttpRequest detailReq = HttpRequest.newBuilder()
                    .uri(URI.create(detailUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> detailRes = httpClient.send(detailReq, HttpResponse.BodyHandlers.ofString());
            JsonNode detailRoot = objectMapper.readTree(detailRes.body());

            String templateCode = "1";
            String series = "C26TSE";
            JsonNode templates = detailRoot.path("data").path("templates");
            if (templates.isArray() && !templates.isEmpty()) {
                templateCode = templates.get(0).path("template_code").asText("1");
                series = templates.get(0).path("invoice_series").asText("C26TSE");
            }

            // Thử kiểm tra hạn ngạch nếu có
            Integer remainingQuota = null;
            try {
                HttpRequest quotaReq = HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl(settings) + "/v1/usage"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                HttpResponse<String> quotaRes = httpClient.send(quotaReq, HttpResponse.BodyHandlers.ofString());
                if (quotaRes.statusCode() == 200) {
                    JsonNode quotaRoot = objectMapper.readTree(quotaRes.body());
                    if (quotaRoot.has("data") && quotaRoot.path("data").has("quota_remaining")) {
                        remainingQuota = quotaRoot.path("data").path("quota_remaining").asInt();
                    }
                }
            } catch (Exception ignored) {}

            return new ConnectionTestResult(
                    true,
                    "Kết nối thành công tới SePay eInvoice (" + (settings.getIsSandbox() ? "Sandbox" : "Production") + ")",
                    providerName,
                    series,
                    templateCode,
                    remainingQuota
            );

        } catch (Exception ex) {
            return new ConnectionTestResult(false, "Lỗi kiểm tra kết nối: " + ex.getMessage(), null, null, null, null);
        }
    }

    /**
     * Tạo và phát hành hóa đơn điện tử cho đơn hàng khóa học
     */
    public IssueResult createAndIssueInvoice(Order order, Course course, BillingSetting settings) {
        String token = getAccessToken(settings);

        String providerAccountId = settings.getEinvoiceProviderAccountId();
        if (providerAccountId == null || providerAccountId.isBlank()) {
            providerAccountId = "f20729d6-b5d9-11f1-b21a-a6006ab65aca"; // Default sandbox provider ID
        }

        String templateCode = settings.getEinvoiceTemplateCode() != null ? settings.getEinvoiceTemplateCode() : "1";
        String series = settings.getEinvoiceInvoiceSeries() != null ? settings.getEinvoiceInvoiceSeries() : "C26TSE";
        long amountLong = order.getAmount().longValue();

        String buyerType = (order.getBuyerType() == InvoiceBuyerType.BUSINESS) ? "enterprise" : "personal";
        String buyerName = order.getCustomerName();
        String legalName = order.getInvoiceCompanyName();
        String taxCode = order.getInvoiceTaxCode();
        String address = (order.getInvoiceAddress() != null && !order.getInvoiceAddress().isBlank())
                ? order.getInvoiceAddress() : "Việt Nam";
        String buyerEmail = (order.getInvoiceEmail() != null && !order.getInvoiceEmail().isBlank())
                ? order.getInvoiceEmail() : order.getCustomerEmail();
        String buyerPhone = order.getCustomerPhone() != null ? order.getCustomerPhone() : "";

        String courseCode = (course != null && course.getCode() != null) ? course.getCode() : "IELTS-COURSE";
        String courseName = (course != null && course.getName() != null) ? course.getName() : "Khóa học IELTS Spells";

        Map<String, Object> buyerMap = new LinkedHashMap<>();
        buyerMap.put("type", buyerType);
        buyerMap.put("name", buyerName);
        if (legalName != null && !legalName.isBlank()) buyerMap.put("legal_name", legalName);
        if (taxCode != null && !taxCode.isBlank()) buyerMap.put("tax_code", taxCode);
        buyerMap.put("address", address);
        buyerMap.put("email", buyerEmail);
        buyerMap.put("phone", buyerPhone);
        buyerMap.put("buyer_code", order.getOrderCode());

        Map<String, Object> itemMap = new LinkedHashMap<>();
        itemMap.put("line_number", 1);
        itemMap.put("line_type", 1);
        itemMap.put("item_code", courseCode);
        itemMap.put("item_name", courseName);
        itemMap.put("unit", "Khóa");
        itemMap.put("quantity", 1);
        itemMap.put("unit_price", amountLong);
        // Giáo dục đào tạo ngoại ngữ theo Luật Thuế GTGT Điều 5 Khoản 13: Không chịu thuế (-2)
        itemMap.put("tax_rate", -2);
        itemMap.put("before_discount_and_tax_amount", amountLong);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("template_code", templateCode);
        payload.put("invoice_series", series);
        payload.put("issued_date", OffsetDateTime.now().format(DATE_FORMATTER));
        payload.put("currency", "VND");
        payload.put("provider_account_id", providerAccountId);
        payload.put("reference_code", order.getOrderCode());
        payload.put("payment_method", "CK");
        payload.put("is_draft", false);
        payload.put("buyer", buyerMap);
        payload.put("items", List.of(itemMap));
        payload.put("notes", "Học phí đào tạo không chịu thuế GTGT theo Điều 5 K13 Luật Thuế GTGT");
        payload.put("total_amount", amountLong);

        // 1. Kiểm tra xem hóa đơn cho mã đơn này đã từng được phát hành thành công chưa (Tránh trùng lặp / hỗ trợ retry tức thời)
        try {
            String checkUrl = getBaseUrl(settings) + "/v1/invoices/create/check/" + order.getOrderCode();
            HttpRequest checkReq = HttpRequest.newBuilder()
                    .uri(URI.create(checkUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> checkRes = httpClient.send(checkReq, HttpResponse.BodyHandlers.ofString());
            if (checkRes.statusCode() == 200) {
                JsonNode checkRoot = objectMapper.readTree(checkRes.body());
                String status = checkRoot.path("data").path("status").asText();
                if ("Success".equalsIgnoreCase(status) || "issued".equalsIgnoreCase(status)) {
                    JsonNode invoiceNode = checkRoot.path("data").path("invoice");
                    String invNum = invoiceNode.path("invoice_number").asText();
                    String invSeries = invoiceNode.path("invoice_series").asText(series);
                    String pdfUrl = invoiceNode.path("pdf_url").asText(null);
                    String xmlUrl = invoiceNode.path("xml_url").asText(null);
                    String cqtCode = invoiceNode.path("tax_authority_code").asText(null);
                    if (cqtCode == null || cqtCode.isBlank() || "null".equalsIgnoreCase(cqtCode)) {
                        cqtCode = "CQT-SANDBOX-" + invNum;
                    }
                    log.info("Tìm thấy hóa đơn đã phát hành sẵn trên SePay cho đơn {}: Số HĐ={}, Series={}, PDF={}",
                            order.getOrderCode(), invNum, invSeries, pdfUrl);
                    return new IssueResult(true, invNum, invSeries, cqtCode, order.getOrderCode(), pdfUrl, xmlUrl, null);
                }
            }
        } catch (Exception ex) {
            log.warn("Không thể kiểm tra trạng thái trước của đơn {} trên SePay: {}", order.getOrderCode(), ex.getMessage());
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            String url = getBaseUrl(settings) + "/v1/invoices/create";

            HttpRequest createReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            log.info("Gửi yêu cầu xuất hóa đơn tới SePay eInvoice cho đơn {}: URL={}", order.getOrderCode(), url);
            HttpResponse<String> response = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());

            // SePay eInvoice trả về 200 (Synchronous) hoặc 202 (Accepted Asynchronous)
            if (response.statusCode() != 200 && response.statusCode() != 202) {
                log.error("SePay eInvoice create failed (HTTP {}): {}", response.statusCode(), response.body());
                return new IssueResult(false, null, null, null, null, null, null, "Lỗi HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String errMsg = root.path("error").path("message").asText("Lỗi từ chối tạo hóa đơn");
                return new IssueResult(false, null, null, null, null, null, null, errMsg);
            }

            String trackingCode = root.path("data").path("tracking_code").asText(order.getOrderCode());

            // Polling checking status (thử tối đa 6 lần, mỗi lần cách 1.5s)
            String checkUrl = getBaseUrl(settings) + "/v1/invoices/create/check/" + trackingCode;
            for (int attempt = 1; attempt <= 6; attempt++) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                HttpRequest checkReq = HttpRequest.newBuilder()
                        .uri(URI.create(checkUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpResponse<String> checkRes = httpClient.send(checkReq, HttpResponse.BodyHandlers.ofString());
                if (checkRes.statusCode() == 200) {
                    JsonNode checkRoot = objectMapper.readTree(checkRes.body());
                    String status = checkRoot.path("data").path("status").asText();

                    if ("Success".equalsIgnoreCase(status) || "issued".equalsIgnoreCase(status)) {
                        JsonNode invoiceNode = checkRoot.path("data").path("invoice");
                        String invNum = invoiceNode.path("invoice_number").asText();
                        String invSeries = invoiceNode.path("invoice_series").asText(series);
                        String pdfUrl = invoiceNode.path("pdf_url").asText(null);
                        String xmlUrl = invoiceNode.path("xml_url").asText(null);
                        String cqtCode = invoiceNode.path("tax_authority_code").asText(null);
                        if (cqtCode == null || cqtCode.isBlank() || "null".equalsIgnoreCase(cqtCode)) {
                            cqtCode = "CQT-SANDBOX-" + invNum;
                        }

                        log.info("Phát hành HĐĐT thành công trên SePay cho đơn {}: Số HĐ={}, Series={}, PDF={}",
                                order.getOrderCode(), invNum, invSeries, pdfUrl);

                        return new IssueResult(
                                true,
                                invNum,
                                invSeries,
                                cqtCode,
                                order.getOrderCode(),
                                pdfUrl,
                                xmlUrl,
                                null
                        );
                    } else if ("Failed".equalsIgnoreCase(status)) {
                        String errMsg = checkRoot.path("data").path("message").asText("Phát hành hóa đơn thất bại");
                        return new IssueResult(false, null, null, null, null, null, null, errMsg);
                    }
                }
            }

            // Hóa đơn đang tiếp tục xử lý bất đồng bộ trên SePay
            return new IssueResult(false, null, null, null, null, null, null, "Hóa đơn đang trong hàng đợi xử lý của SePay");

        } catch (Exception ex) {
            log.error("Ngoại lệ khi gọi SePay eInvoice cho đơn {}: {}", order.getOrderCode(), ex.getMessage(), ex);
            return new IssueResult(false, null, null, null, null, null, null, ex.getMessage());
        }
    }
}
