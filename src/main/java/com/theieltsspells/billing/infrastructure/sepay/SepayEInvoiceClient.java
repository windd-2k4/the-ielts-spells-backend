package com.theieltsspells.billing.infrastructure.sepay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.billing.domain.BillingSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SepayEInvoiceClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern CQT_XML_PATTERN = Pattern.compile(
            "(?is)<(?:\\w+:)?MCCQT\\b[^>]*>\\s*([^<]+?)\\s*</(?:\\w+:)?MCCQT>"
    );
    private static final int MAX_INVOICE_XML_LENGTH = 5_000_000;

    private final ObjectMapper objectMapper;
    private final SepayEInvoiceTokenService tokenService;
    private HttpClient httpClient;

    @FunctionalInterface
    private interface RequestFactory {
        HttpRequest create(String token);
    }

    public record CreateInvoicePayload(
            String templateCode,
            String invoiceSeries,
            String providerAccountId,
            String referenceCode,
            String paymentMethod,
            boolean isDraft,
            String buyerType,
            String buyerName,
            String buyerLegalName,
            String buyerTaxCode,
            String buyerAddress,
            String buyerEmail,
            String buyerPhone,
            String buyerCode,
            String itemCode,
            String itemName,
            String unit,
            int quantity,
            long unitPrice,
            Integer taxRate,
            String notes
    ) {}

    public record CreateResult(
            boolean success,
            String trackingCode,
            String errorCode,
            String errorMessage
    ) {}

    public record CheckStatusResult(
            boolean completed,
            boolean success,
            boolean isDraft,
            boolean isPending,
            boolean isUndetermined,
            OffsetDateTime nextRetryAt,
            Integer retriesCount,
            Integer maxRetries,
            String invoiceNumber,
            String invoiceSeries,
            String cqtCode,
            String lookupCode,
            String pdfUrl,
            String xmlUrl,
            String errorCode,
            String errorMessage
    ) {
        public static CheckStatusResult successIssued(String invNum, String series, String cqt, String lookup, String pdf, String xml) {
            return new CheckStatusResult(true, true, false, false, false, null, null, null, invNum, series, cqt, lookup, pdf, xml, null, null);
        }

        public static CheckStatusResult successDraft(String invNum, String series, String cqt, String lookup, String pdf, String xml) {
            return new CheckStatusResult(true, true, true, false, false, null, null, null, invNum, series, cqt, lookup, pdf, xml, null, null);
        }

        public static CheckStatusResult pending(OffsetDateTime nextRetry, Integer retriesCount, Integer maxRetries, String message) {
            return new CheckStatusResult(false, false, false, true, false, nextRetry, retriesCount, maxRetries, null, null, null, null, null, null, null, message);
        }

        public static CheckStatusResult undetermined(String message) {
            return new CheckStatusResult(true, false, false, false, true, null, null, null, null, null, null, null, null, null, "UNDETERMINED", message);
        }

        public static CheckStatusResult failed(String errorCode, String message) {
            return new CheckStatusResult(true, false, false, false, false, null, null, null, null, null, null, null, null, null, errorCode, message);
        }
    }

    public record InvoiceDetailResult(
            boolean success,
            boolean notFound,
            String id,
            String referenceCode,
            String status,
            String invoiceNumber,
            String invoiceSeries,
            String templateCode,
            String issuedDate,
            String cqtCode,
            String lookupCode,
            String pdfUrl,
            String xmlUrl,
            Long totalAmount,
            Integer taxRate,
            String errorCode,
            String errorMessage
    ) {
        public static InvoiceDetailResult ofNotFound() {
            return new InvoiceDetailResult(false, true, null, null, null, null, null, null, null, null, null, null, null, null, null, "NOT_FOUND", "Không tìm thấy hóa đơn trên SePay");
        }
        public static InvoiceDetailResult error(String code, String message) {
            return new InvoiceDetailResult(false, false, null, null, null, null, null, null, null, null, null, null, null, null, null, code, message);
        }
        public static InvoiceDetailResult ofSuccess(String invNum, String series, String cqt, String lookup, String pdf, String xml) {
            return new InvoiceDetailResult(true, false, UUID.randomUUID().toString(), null, "signed", invNum, series, "1", "2026-09-22", cqt, lookup, pdf, xml, 5000000L, -2, null, null);
        }
        public static InvoiceDetailResult ofDraft(String invNum, String series, String lookup) {
            return new InvoiceDetailResult(true, false, UUID.randomUUID().toString(), null, "draft", invNum, series, "1", "2026-09-22", null, lookup, null, null, 5000000L, -2, null, null);
        }
        public static InvoiceDetailResult ofPending(String message) {
            return new InvoiceDetailResult(false, false, null, null, "processing", null, null, null, null, null, null, null, null, null, null, "PENDING", message);
        }
    }

    public record IssueDraftResult(
            boolean success,
            String trackingCode,
            String errorCode,
            String errorMessage
    ) {}

    public record TemplateDto(
            String templateCode,
            String invoiceSeries,
            String templateName,
            Integer taxRate
    ) {}

    public record ProviderAccountDto(
            String id,
            String provider,
            String taxCode,
            String legalName,
            boolean active,
            String taxAuthorityApprovedDate,
            List<TemplateDto> templates
    ) {}

    public record ConnectionTestResult(
            boolean success,
            String message,
            String providerName,
            String providerAccountId,
            String invoiceSeries,
            String templateCode,
            String taxAuthorityApprovedDate,
            Integer remainingQuota
    ) {}

    public record DownloadResult(
            boolean success,
            String fileType,
            String fileName,
            byte[] content,
            String errorCode,
            String errorMessage
    ) {}

    private HttpResponse<String> sendWithAuth(BillingSetting settings, RequestFactory requestFactory) throws Exception {
        String token = tokenService.getAccessToken(settings);
        HttpRequest request = requestFactory.create(token);
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // Nếu token hết hạn hoặc bị thu hồi (401), evict cache và thử lại đúng 1 lần với token mới
        if (response.statusCode() == 401) {
            log.warn("SePay eInvoice trả về HTTP 401 Unauthorized. Tiến hành xóa token cache và thử lại...");
            tokenService.evictToken();
            String freshToken = tokenService.getAccessToken(settings);
            HttpRequest retryRequest = requestFactory.create(freshToken);
            return httpClient().send(retryRequest, HttpResponse.BodyHandlers.ofString());
        }

        return response;
    }

    private synchronized HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return httpClient;
    }

    /**
     * Lấy danh sách Provider Accounts đang kết nối trên SePay
     */
    public List<ProviderAccountDto> getProviderAccounts(BillingSetting settings) {
        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/provider-accounts?page=1&per_page=50";

        try {
            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build()
            );

            if (response.statusCode() != 200) {
                log.error("Lỗi lấy danh sách Provider Accounts (HTTP {}). Nội dung phản hồi đã được ẩn.", response.statusCode());
                throw new IllegalStateException("SePay từ chối truy vấn Provider Account (HTTP " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                throw new IllegalStateException("SePay trả về dữ liệu Provider Account không đúng định dạng");
            }

            List<ProviderAccountDto> result = new ArrayList<>();
            for (JsonNode item : items) {
                String id = item.path("id").asText();
                String provider = item.path("provider").asText();
                String taxCode = item.path("tax_code").asText(null);
                String legalName = item.path("legal_name").asText(null);
                boolean active = item.path("active").asBoolean(false);
                String approvedDate = item.path("tax_authority_approved_date").asText(null);

                // Fetch detail để lấy danh sách templates & series
                List<TemplateDto> templates = getTemplatesForProvider(settings, id);

                result.add(new ProviderAccountDto(id, provider, taxCode, legalName, active, approvedDate, templates));
            }

            return result;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Ngoại lệ khi lấy danh sách Provider Accounts từ SePay: {}", ex.getMessage());
            throw new IllegalStateException("Không thể đọc danh sách Provider Account từ SePay", ex);
        }
    }

    /**
     * Lấy chi tiết Provider Account (kèm danh sách Mẫu số và Ký hiệu hóa đơn)
     */
    public List<TemplateDto> getTemplatesForProvider(BillingSetting settings, String providerAccountId) {
        if (providerAccountId == null || providerAccountId.isBlank()) {
            return Collections.emptyList();
        }

        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/provider-accounts/" + providerAccountId.trim();

        try {
            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(12))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build()
            );

            if (response.statusCode() != 200) {
                log.warn("Không thể lấy chi tiết Provider Account {} (HTTP {}). Nội dung phản hồi đã được ẩn.", providerAccountId, response.statusCode());
                throw new IllegalStateException("SePay từ chối truy vấn chi tiết Provider Account (HTTP " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode templatesNode = root.path("data").path("templates");
            if (!templatesNode.isArray()) {
                throw new IllegalStateException("SePay trả về dữ liệu mẫu hóa đơn không đúng định dạng");
            }

            List<TemplateDto> list = new ArrayList<>();
            for (JsonNode t : templatesNode) {
                String tCode = t.path("template_code").asText("1");
                String series = t.path("invoice_series").asText("");
                String name = t.path("template_name").asText(t.path("invoice_label").asText(t.path("name").asText("")));
                Integer rate = t.has("tax_rate") && !t.path("tax_rate").isNull() ? t.path("tax_rate").asInt() : null;
                list.add(new TemplateDto(tCode, series, name, rate));
            }
            return list;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Ngoại lệ khi lấy templates cho provider {}: {}", providerAccountId, ex.getMessage());
            throw new IllegalStateException("Không thể đọc chi tiết Provider Account từ SePay", ex);
        }
    }

    /**
     * Lấy hạn ngạch hóa đơn còn lại
     */
    public Integer getRemainingQuota(BillingSetting settings) {
        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/usage";

        try {
            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build()
            );

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("data") && root.path("data").has("quota_remaining")) {
                    return root.path("data").path("quota_remaining").asInt();
                }
            }
        } catch (Exception ex) {
            log.warn("Không thể kiểm tra hạn ngạch SePay eInvoice: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Kiểm tra trạng thái kết nối tổng thể SePay eInvoice
     */
    public ConnectionTestResult testConnection(BillingSetting settings) {
        try {
            List<ProviderAccountDto> providers = getProviderAccounts(settings);
            if (providers.isEmpty()) {
                return new ConnectionTestResult(
                        false,
                        "Client ID/Secret Sandbox đã xác thực thành công, nhưng API /v1/provider-accounts trả về danh sách rỗng. "
                                + "Tài khoản SePay này chưa được gắn Provider Account Sandbox; hãy yêu cầu SePay/Mắt Bão kích hoạt tài khoản nhà cung cấp thử nghiệm. "
                                + "Không sử dụng UUID ví dụ trong tài liệu.",
                        null, null, null, null, null, null
                );
            }

            String configuredProviderId = settings.getActiveProviderAccountId();
            ProviderAccountDto activeProvider = providers.stream()
                    .filter(ProviderAccountDto::active)
                    .filter(provider -> configuredProviderId == null || configuredProviderId.isBlank()
                            || provider.id().equals(configuredProviderId))
                    .findFirst()
                    .orElse(null);
            if (activeProvider == null) {
                return new ConnectionTestResult(
                        false,
                        configuredProviderId == null || configuredProviderId.isBlank()
                                ? "Có Provider Account nhưng chưa có tài khoản nào ở trạng thái active"
                                : "Provider Account cấu hình không tồn tại hoặc chưa active",
                        null, null, null, null, null, null
                );
            }

            if (activeProvider.templates() == null || activeProvider.templates().isEmpty()) {
                return new ConnectionTestResult(
                        false,
                        "Provider Account đã active nhưng chưa có mẫu số/ký hiệu hóa đơn khả dụng",
                        activeProvider.provider(), activeProvider.id(), null, null,
                        activeProvider.taxAuthorityApprovedDate(), null
                );
            }
            String configuredTemplate = settings.getActiveTemplateCode();
            String configuredSeries = settings.getActiveInvoiceSeries();
            TemplateDto selectedTemplate = activeProvider.templates().stream()
                    .filter(template -> configuredTemplate == null || configuredTemplate.isBlank()
                            || configuredTemplate.equals(template.templateCode()))
                    .filter(template -> configuredSeries == null || configuredSeries.isBlank()
                            || configuredSeries.equals(template.invoiceSeries()))
                    .findFirst()
                    .orElse(null);
            if (selectedTemplate == null) {
                return new ConnectionTestResult(
                        false,
                        "Mẫu số/ký hiệu cấu hình không tồn tại trên Provider Account đang active",
                        activeProvider.provider(), activeProvider.id(), null, null,
                        activeProvider.taxAuthorityApprovedDate(), null
                );
            }
            String templateCode = selectedTemplate.templateCode();
            String series = selectedTemplate.invoiceSeries();

            Integer quota = getRemainingQuota(settings);

            boolean isProd = settings.isProductionContext();
            String envName = isProd ? "Production" : "Sandbox";

            return new ConnectionTestResult(
                    true,
                    "Kết nối thành công tới SePay eInvoice (" + envName + ")",
                    activeProvider.provider(),
                    activeProvider.id(),
                    series,
                    templateCode,
                    activeProvider.taxAuthorityApprovedDate(),
                    quota
            );
        } catch (Exception ex) {
            log.error("Lỗi kiểm tra kết nối SePay eInvoice: {}", ex.getMessage());
            return new ConnectionTestResult(false, "Lỗi kiểm tra kết nối: " + ex.getMessage(), null, null, null, null, null, null);
        }
    }

    /**
     * Gửi yêu cầu Tạo hóa đơn mới (POST /v1/invoices/create)
     * Trả về tracking_code để poll trạng thái
     */
    public CreateResult createInvoice(BillingSetting settings, CreateInvoicePayload p) {
        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/invoices/create";

        try {
            Map<String, Object> buyerMap = new LinkedHashMap<>();
            String bType = "personal";
            if (p.buyerType() != null) {
                String lower = p.buyerType().trim().toLowerCase();
                if ("business".equals(lower) || "company".equals(lower)) {
                    bType = "company";
                } else {
                    bType = "personal";
                }
            }
            buyerMap.put("type", bType);
            buyerMap.put("name", p.buyerName() != null ? p.buyerName() : "Khách hàng");
            if (p.buyerLegalName() != null && !p.buyerLegalName().isBlank()) {
                buyerMap.put("legal_name", p.buyerLegalName().trim());
            }
            if (p.buyerTaxCode() != null && !p.buyerTaxCode().isBlank()) {
                buyerMap.put("tax_code", p.buyerTaxCode().trim());
            }
            buyerMap.put("address", (p.buyerAddress() != null && !p.buyerAddress().isBlank()) ? p.buyerAddress() : "Việt Nam");
            buyerMap.put("email", p.buyerEmail() != null ? p.buyerEmail() : "");
            buyerMap.put("phone", p.buyerPhone() != null ? p.buyerPhone() : "");
            buyerMap.put("buyer_code", p.buyerCode() != null ? p.buyerCode() : p.referenceCode());

            long totalAmount = p.unitPrice() * p.quantity();

            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("line_number", 1);
            itemMap.put("line_type", 1);
            itemMap.put("item_code", p.itemCode() != null ? p.itemCode() : "COURSE");
            itemMap.put("item_name", p.itemName() != null ? p.itemName() : "Khóa học đào tạo IELTS Spells");
            itemMap.put("unit", p.unit() != null ? p.unit() : "Khóa");
            itemMap.put("quantity", p.quantity() > 0 ? p.quantity() : 1);
            itemMap.put("unit_price", p.unitPrice());
            if (p.taxRate() != null) {
                itemMap.put("tax_rate", p.taxRate());
            }
            itemMap.put("before_discount_and_tax_amount", totalAmount);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("template_code", p.templateCode());
            payload.put("invoice_series", p.invoiceSeries());
            payload.put("issued_date", OffsetDateTime.now().format(DATE_FORMATTER));
            payload.put("currency", "VND");
            payload.put("provider_account_id", p.providerAccountId());
            payload.put("reference_code", p.referenceCode());
            payload.put("payment_method", p.paymentMethod() != null ? p.paymentMethod() : "CK");
            payload.put("is_draft", p.isDraft());
            payload.put("buyer", buyerMap);
            payload.put("items", List.of(itemMap));
            if (p.notes() != null && !p.notes().isBlank()) {
                payload.put("notes", p.notes());
            }
            payload.put("total_amount", totalAmount);

            String requestBody = objectMapper.writeValueAsString(payload);
            log.info("Gửi POST /v1/invoices/create [ref={}]: is_draft={}, template={}, series={}",
                    p.referenceCode(), p.isDraft(), p.templateCode(), p.invoiceSeries());

            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                            .build()
            );

            log.info("POST /v1/invoices/create [ref={}] hoàn tất với HTTP {}. Nội dung phản hồi không được ghi log.",
                    p.referenceCode(), response.statusCode());

            if (response.statusCode() != 200 && response.statusCode() != 202) {
                String errBody = response.body();
                try {
                    JsonNode errNode = objectMapper.readTree(errBody);
                    String errCode = errNode.path("error").path("code").asText("HTTP_" + response.statusCode());
                    String errMsg = errNode.path("error").path("message").asText("SePay từ chối tạo hóa đơn");
                    return new CreateResult(false, null, errCode, errMsg);
                } catch (Exception e) {
                    return new CreateResult(false, null, "HTTP_" + response.statusCode(), "SePay trả về HTTP " + response.statusCode());
                }
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String errCode = root.path("error").path("code").asText("UNKNOWN_ERROR");
                String errMsg = root.path("error").path("message").asText("Lỗi từ chối tạo hóa đơn");
                return new CreateResult(false, null, errCode, errMsg);
            }

            String trackingCode = root.path("data").path("tracking_code").asText(null);
            return new CreateResult(true, trackingCode, null, null);

        } catch (Exception ex) {
            log.error("Ngoại lệ khi gọi createInvoice [ref={}]: {}", p.referenceCode(), ex.getMessage(), ex);
            return new CreateResult(false, null, "EXCEPTION", ex.getMessage());
        }
    }

    /**
     * Tra cứu chi tiết hóa đơn từ SePay theo reference_code (GET /v1/invoices/{reference_code})
     */
    public InvoiceDetailResult getInvoiceDetail(BillingSetting settings, String referenceCode) {
        if (referenceCode == null || referenceCode.isBlank()) {
            return InvoiceDetailResult.error("INVALID_REFERENCE", "reference_code không được để trống");
        }
        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/invoices/" + referenceCode.trim();

        try {
            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(12))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build()
            );

            log.info("GET /v1/invoices/{} -> HTTP {}", referenceCode, response.statusCode());

            if (response.statusCode() == 404) {
                return InvoiceDetailResult.ofNotFound();
            }

            if (response.statusCode() != 200) {
                String errBody = response.body();
                try {
                    JsonNode errNode = objectMapper.readTree(errBody);
                    String errCode = errNode.path("error").path("code").asText("HTTP_" + response.statusCode());
                    String errMsg = errNode.path("error").path("message").asText("Không thể lấy chi tiết hóa đơn từ SePay");
                    return InvoiceDetailResult.error(errCode, errMsg);
                } catch (Exception e) {
                    return InvoiceDetailResult.error("HTTP_" + response.statusCode(), "SePay trả về HTTP " + response.statusCode());
                }
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String errCode = root.path("error").path("code").asText("UNKNOWN_ERROR");
                String errMsg = root.path("error").path("message").asText("Lỗi lấy chi tiết hóa đơn");
                return InvoiceDetailResult.error(errCode, errMsg);
            }

            JsonNode data = root.path("data");
            String id = data.path("id").asText(null);
            String ref = data.path("reference_code").asText(referenceCode);
            String status = data.path("status").asText("issued");
            String invNum = data.path("invoice_number").asText(null);
            String series = data.path("invoice_series").asText(null);
            String tmpl = data.path("template_code").asText(null);
            String issuedDate = data.path("issued_date").asText(null);
            String cqt = data.path("tax_authority_code").asText(null);
            String lookup = data.path("lookup_code").asText(null);
            String pdf = data.path("pdf_url").asText(null);
            String xml = data.path("xml_url").asText(null);
            if ((cqt == null || cqt.isBlank()) && xml != null && !xml.isBlank()) {
                cqt = fetchTaxAuthorityCodeFromXml(xml);
            }
            Long total = data.has("total_amount") && !data.path("total_amount").isNull() ? data.path("total_amount").asLong() : null;
            Integer taxRate = data.has("tax_rate") && !data.path("tax_rate").isNull() ? data.path("tax_rate").asInt() : null;

            return new InvoiceDetailResult(true, false, id, ref, status, invNum, series, tmpl, issuedDate, cqt, lookup, pdf, xml, total, taxRate, null, null);
        } catch (Exception ex) {
            log.error("Ngoại lệ khi gọi GET /v1/invoices/{}: {}", referenceCode, ex.getMessage(), ex);
            return InvoiceDetailResult.error("EXCEPTION", ex.getMessage());
        }
    }

    private String fetchTaxAuthorityCodeFromXml(String xmlUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(xmlUrl))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || response.body() == null || response.body().length() > MAX_INVOICE_XML_LENGTH) {
                return null;
            }
            return extractTaxAuthorityCode(response.body());
        } catch (Exception ex) {
            log.debug("Chưa đọc được mã CQT từ XML SePay: {}", ex.getMessage());
            return null;
        }
    }

    static String extractTaxAuthorityCode(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        Matcher matcher = CQT_XML_PATTERN.matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * Kiểm tra trạng thái xử lý tạo hóa đơn (GET /v1/invoices/create/check/{tracking_code})
     */
    public CheckStatusResult checkCreateStatus(BillingSetting settings, String trackingCode) {
        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/invoices/create/check/" + trackingCode.trim();

        try {
            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(12))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build()
            );

            if (response.statusCode() != 200) {
                log.warn("Lỗi kiểm tra create status tracking {} (HTTP {}). Nội dung phản hồi đã được ẩn.", trackingCode, response.statusCode());
                return CheckStatusResult.failed("HTTP_" + response.statusCode(), "SePay trả về HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String status = root.path("data").path("status").asText("");

            if ("Success".equalsIgnoreCase(status) || "issued".equalsIgnoreCase(status) || "draft".equalsIgnoreCase(status)) {
                JsonNode inv = root.path("data").path("invoice");
                String invNum = inv.path("invoice_number").asText(null);
                String series = inv.path("invoice_series").asText(null);
                String cqtCode = inv.path("tax_authority_code").asText(null);
                String lookupCode = inv.path("lookup_code").asText(null);
                String pdfUrl = inv.path("pdf_url").asText(null);
                String xmlUrl = inv.path("xml_url").asText(null);
                String invStatus = inv.path("status").asText(status);
                boolean isDraft = "draft".equalsIgnoreCase(invStatus);

                if (isDraft) {
                    return CheckStatusResult.successDraft(invNum, series, cqtCode, lookupCode, pdfUrl, xmlUrl);
                } else {
                    return CheckStatusResult.successIssued(invNum, series, cqtCode, lookupCode, pdfUrl, xmlUrl);
                }
            } else if ("Failed".equalsIgnoreCase(status)) {
                String errMsg = root.path("data").path("message").asText("Tạo hóa đơn thất bại");
                String errCode = root.path("data").path("error_code").asText("FAILED");
                if (errMsg.contains("chưa xác nhận được kết quả") || errMsg.contains("chưa xác định")) {
                    return CheckStatusResult.undetermined(errMsg);
                }
                return CheckStatusResult.failed(errCode, errMsg);
            } else {
                // Pending / Processing
                JsonNode retryNode = root.path("data").path("retry");
                OffsetDateTime nextRetryAt = null;
                Integer retriesCount = null;
                Integer maxRetries = null;
                if (!retryNode.isMissingNode() && !retryNode.isNull()) {
                    if (retryNode.has("retries_count")) retriesCount = retryNode.path("retries_count").asInt();
                    if (retryNode.has("max_retries")) maxRetries = retryNode.path("max_retries").asInt();
                    if (retryNode.has("next_retry_at") && !retryNode.path("next_retry_at").isNull()) {
                        nextRetryAt = parseOffsetDateTime(retryNode.path("next_retry_at").asText());
                    }
                }
                String msg = root.path("data").path("message").asText("Hóa đơn đang trong hàng đợi xử lý của SePay");
                return CheckStatusResult.pending(nextRetryAt, retriesCount, maxRetries, msg);
            }
        } catch (Exception ex) {
            log.error("Ngoại lệ khi kiểm tra create status tracking {}: {}", trackingCode, ex.getMessage());
            return CheckStatusResult.failed("EXCEPTION", ex.getMessage());
        }
    }

    /**
     * Phát hành hóa đơn nháp (Flow B: POST /v1/invoices/issue)
     * Body: { "reference_code": "..." }
     */
    public IssueDraftResult issueDraft(BillingSetting settings, String referenceCode) {
        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/invoices/issue";

        try {
            Map<String, String> body = Map.of("reference_code", referenceCode);
            String jsonBody = objectMapper.writeValueAsString(body);

            log.info("Gửi POST /v1/invoices/issue [ref={}]: {}", referenceCode, jsonBody);

            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                            .build()
            );

            log.info("POST /v1/invoices/issue [ref={}] hoàn tất với HTTP {}. Nội dung phản hồi không được ghi log.",
                    referenceCode, response.statusCode());

            if (response.statusCode() != 200 && response.statusCode() != 202) {
                String errBody = response.body();
                try {
                    JsonNode errNode = objectMapper.readTree(errBody);
                    String errCode = errNode.path("error").path("code").asText("HTTP_" + response.statusCode());
                    String errMsg = errNode.path("error").path("message").asText("SePay từ chối phát hành hóa đơn nháp");
                    return new IssueDraftResult(false, null, errCode, errMsg);
                } catch (Exception e) {
                    return new IssueDraftResult(false, null, "HTTP_" + response.statusCode(), "SePay trả về HTTP " + response.statusCode());
                }
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String errCode = root.path("error").path("code").asText("UNKNOWN_ERROR");
                String errMsg = root.path("error").path("message").asText("Từ chối phát hành hóa đơn nháp");
                return new IssueDraftResult(false, null, errCode, errMsg);
            }

            String trackingCode = root.path("data").path("tracking_code").asText(null);
            return new IssueDraftResult(true, trackingCode, null, null);

        } catch (Exception ex) {
            log.error("Ngoại lệ khi issueDraft [ref={}]: {}", referenceCode, ex.getMessage());
            return new IssueDraftResult(false, null, "EXCEPTION", ex.getMessage());
        }
    }

    /**
     * Kiểm tra trạng thái phát hành hóa đơn nháp (GET /v1/invoices/issue/check/{tracking_code})
     */
    public CheckStatusResult checkIssueStatus(BillingSetting settings, String trackingCode) {
        String baseUrl = tokenService.getBaseUrl(settings);
        String url = baseUrl + "/v1/invoices/issue/check/" + trackingCode.trim();

        try {
            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(12))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build()
            );

            if (response.statusCode() != 200) {
                log.warn("Lỗi kiểm tra issue status tracking {} (HTTP {}). Nội dung phản hồi đã được ẩn.", trackingCode, response.statusCode());
                return CheckStatusResult.failed("HTTP_" + response.statusCode(), "SePay trả về HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String status = root.path("data").path("status").asText("");

            if ("Success".equalsIgnoreCase(status) || "issued".equalsIgnoreCase(status)) {
                JsonNode inv = root.path("data").path("invoice");
                String invNum = inv.path("invoice_number").asText(null);
                String series = inv.path("invoice_series").asText(null);
                String cqtCode = inv.path("tax_authority_code").asText(null);
                String lookupCode = inv.path("lookup_code").asText(null);
                String pdfUrl = inv.path("pdf_url").asText(null);
                String xmlUrl = inv.path("xml_url").asText(null);

                return CheckStatusResult.successIssued(invNum, series, cqtCode, lookupCode, pdfUrl, xmlUrl);
            } else if ("Failed".equalsIgnoreCase(status)) {
                String errMsg = root.path("data").path("message").asText("Phát hành hóa đơn thất bại");
                String errCode = root.path("data").path("error_code").asText("FAILED");
                if (errMsg.contains("chưa xác nhận được kết quả") || errMsg.contains("chưa xác định")) {
                    return CheckStatusResult.undetermined(errMsg);
                }
                return CheckStatusResult.failed(errCode, errMsg);
            } else {
                JsonNode retryNode = root.path("data").path("retry");
                OffsetDateTime nextRetryAt = null;
                Integer retriesCount = null;
                Integer maxRetries = null;
                if (!retryNode.isMissingNode() && !retryNode.isNull()) {
                    if (retryNode.has("retries_count")) retriesCount = retryNode.path("retries_count").asInt();
                    if (retryNode.has("max_retries")) maxRetries = retryNode.path("max_retries").asInt();
                    if (retryNode.has("next_retry_at") && !retryNode.path("next_retry_at").isNull()) {
                        nextRetryAt = parseOffsetDateTime(retryNode.path("next_retry_at").asText());
                    }
                }
                String msg = root.path("data").path("message").asText("Hóa đơn đang trong hàng đợi phát hành của SePay");
                return CheckStatusResult.pending(nextRetryAt, retriesCount, maxRetries, msg);
            }
        } catch (Exception ex) {
            log.error("Ngoại lệ khi kiểm tra issue status tracking {}: {}", trackingCode, ex.getMessage());
            return CheckStatusResult.failed("EXCEPTION", ex.getMessage());
        }
    }

    private OffsetDateTime parseOffsetDateTime(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return OffsetDateTime.parse(str);
        } catch (Exception e1) {
            try {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(str, DATE_FORMATTER);
                return ldt.atOffset(java.time.ZoneOffset.ofHours(7));
            } catch (Exception e2) {
                log.warn("Không thể parse date '{}': {}", str, e2.getMessage());
                return null;
            }
        }
    }

    /**
     * Tải URL tài liệu hóa đơn (PDF hoặc XML)
     */
    public String getDownloadUrl(BillingSetting settings, String trackingCode, String type) {
        String baseUrl = tokenService.getBaseUrl(settings);
        return String.format("%s/v1/invoices/%s/download?type=%s", baseUrl, trackingCode, type);
    }

    public DownloadResult downloadInvoiceFile(BillingSetting settings, String trackingCode, String type) {
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!("pdf".equals(normalizedType) || "xml".equals(normalizedType))) {
            return new DownloadResult(false, null, null, null, "INVALID_TYPE", "Chỉ hỗ trợ tải pdf hoặc xml");
        }

        String url = getDownloadUrl(settings, trackingCode, normalizedType);
        try {
            HttpResponse<String> response = sendWithAuth(settings, token ->
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build()
            );
            if (response.statusCode() != 200) {
                log.warn("Không thể tải file {} cho tracking {} (HTTP {}). Nội dung phản hồi đã được ẩn.",
                        normalizedType, trackingCode, response.statusCode());
                return new DownloadResult(false, null, null, null,
                        "HTTP_" + response.statusCode(), "SePay trả về HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                return new DownloadResult(false, null, null, null,
                        root.path("error").path("code").asText("DOWNLOAD_FAILED"),
                        root.path("error").path("message").asText("SePay từ chối tải file hóa đơn"));
            }
            JsonNode data = root.path("data");
            byte[] content = Base64.getDecoder().decode(data.path("content").asText(""));
            return new DownloadResult(true,
                    data.path("file_type").asText(normalizedType),
                    data.path("file_name").asText(null),
                    content,
                    null,
                    null);
        } catch (Exception ex) {
            log.error("Ngoại lệ khi tải file {} cho tracking {}: {}", normalizedType, trackingCode, ex.getMessage());
            return new DownloadResult(false, null, null, null, "EXCEPTION", ex.getMessage());
        }
    }
}
