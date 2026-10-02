package com.theieltsspells.billing.infrastructure.sepay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.billing.domain.BillingSetting;
import com.theieltsspells.billing.infrastructure.security.SecretEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class SepayEInvoiceTokenService {

    public static final String SANDBOX_BASE_URL = "https://einvoice-api-sandbox.sepay.vn";
    public static final String PRODUCTION_BASE_URL = "https://einvoice-api.sepay.vn";

    private final ObjectMapper objectMapper;
    private final SecretEncryptionService secretEncryptionService;
    private HttpClient httpClient;

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>(null);

    @Value("${SEPAY_EINVOICE_SANDBOX_CLIENT_ID:${SEPAY_EINVOICE_CLIENT_ID:}}")
    private String sandboxClientId = "";

    @Value("${SEPAY_EINVOICE_SANDBOX_CLIENT_SECRET:${SEPAY_EINVOICE_CLIENT_SECRET:}}")
    private String sandboxClientSecret = "";

    @Value("${SEPAY_EINVOICE_PRODUCTION_CLIENT_ID:}")
    private String productionClientId = "";

    @Value("${SEPAY_EINVOICE_PRODUCTION_CLIENT_SECRET:}")
    private String productionClientSecret = "";

    @Autowired
    public SepayEInvoiceTokenService(ObjectMapper objectMapper, SecretEncryptionService secretEncryptionService) {
        this.objectMapper = objectMapper;
        this.secretEncryptionService = secretEncryptionService;
    }

    /**
     * Backward-compatible constructor for standalone tests
     */
    public SepayEInvoiceTokenService(ObjectMapper objectMapper) {
        this(objectMapper, new SecretEncryptionService());
    }

    private record CachedToken(
            String token,
            Instant expiresAt,
            String clientFingerprint
    ) {}

    public String getBaseUrl(BillingSetting settings) {
        return settings.isProductionContext() ? PRODUCTION_BASE_URL : SANDBOX_BASE_URL;
    }

    /**
     * Lấy Bearer Access Token từ SePay eInvoice API.
     * Tự động cache token trong bộ nhớ (hiệu lực 24h) và trừ buffer an toàn 5 phút.
     */
    public synchronized String getAccessToken(BillingSetting settings) {
        Credentials credentials = resolveCredentials(settings, settings.isProductionContext());
        String clientId = credentials.clientId();
        String rawSecret = credentials.clientSecret();

        if (clientId == null || clientId.isBlank() || rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình SePay eInvoice Client ID hoặc Client Secret trong cài đặt hệ thống");
        }

        String clientSecret = secretEncryptionService.decrypt(rawSecret);
        String baseUrl = getBaseUrl(settings);
        String fingerprint = clientId.trim() + "@" + baseUrl;

        CachedToken current = tokenCache.get();
        if (current != null
                && Instant.now().isBefore(current.expiresAt())
                && fingerprint.equals(current.clientFingerprint())) {
            return current.token();
        }

        return requestNewToken(clientId.trim(), clientSecret.trim(), baseUrl, fingerprint);
    }

    /**
     * Kiểm tra handshake và xin cấp OAuth2 token trực tiếp từ môi trường SePay Production
     * Dùng cho Production Readiness 15-point health check mà không làm ảnh hưởng trạng thái đang chạy.
     */
    public String verifyProductionHandshake(BillingSetting settings) {
        Credentials credentials = resolveCredentials(settings, true);
        String prodClientId = credentials.clientId();
        String rawProdSecret = credentials.clientSecret();

        if (prodClientId == null || prodClientId.isBlank() || rawProdSecret == null || rawProdSecret.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình SePay Production Client ID hoặc Client Secret");
        }

        String prodClientSecret = secretEncryptionService.decrypt(rawProdSecret);
        String fingerprint = prodClientId.trim() + "@" + PRODUCTION_BASE_URL;

        return requestNewToken(prodClientId.trim(), prodClientSecret.trim(), PRODUCTION_BASE_URL, fingerprint);
    }

    public boolean hasProductionCredentials(BillingSetting settings) {
        Credentials credentials = resolveCredentials(settings, true);
        return hasText(credentials.clientId()) && hasText(credentials.clientSecret());
    }

    public boolean hasSandboxCredentials(BillingSetting settings) {
        Credentials credentials = resolveCredentials(settings, false);
        return hasText(credentials.clientId()) && hasText(credentials.clientSecret());
    }

    public boolean areProductionAndSandboxCredentialsSeparated(BillingSetting settings) {
        Credentials production = resolveCredentials(settings, true);
        Credentials sandbox = resolveCredentials(settings, false);
        if (!hasText(production.clientId())) return false;
        if (!hasText(sandbox.clientId())) return true;
        return !production.clientId().trim().equalsIgnoreCase(sandbox.clientId().trim());
    }

    /**
     * Xóa cache token hiện tại (dùng khi nhận HTTP 401 Unauthorized để buộc refresh)
     */
    public void evictToken() {
        log.warn("SePay eInvoice Token Cache bị hủy (evict). Sẽ yêu cầu token mới ở lần gọi kế tiếp.");
        tokenCache.set(null);
    }

    private String requestNewToken(String clientId, String clientSecret, String baseUrl, String fingerprint) {
        try {
            String maskedClientId = mask(clientId);
            log.info("Đang yêu cầu SePay eInvoice Token mới từ {}: ClientID={}", baseUrl, maskedClientId);

            String credentials = clientId + ":" + clientSecret;
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            String url = baseUrl + "/v1/token";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", basicAuth)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Lỗi yêu cầu SePay eInvoice token (HTTP {}). Nội dung phản hồi đã được ẩn.", response.statusCode());
                throw new IllegalStateException("Xác thực SePay eInvoice thất bại (HTTP " + response.statusCode() + "): Vui lòng kiểm tra Client ID & Client Secret");
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String errMsg = root.path("error").path("message").asText("Lỗi từ chối cấp token");
                throw new IllegalStateException("SePay eInvoice từ chối cấp token: " + errMsg);
            }

            String token = root.path("data").path("access_token").asText();
            int expiresIn = root.path("data").path("expires_in").asInt(86400);

            // Buffer an toàn: hết hạn sớm hơn 5 phút (hoặc tối thiểu 60s)
            Instant expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 300));
            tokenCache.set(new CachedToken(token, expiresAt, fingerprint));

            log.info("Lấy SePay eInvoice Token thành công. Hết hạn sau {}s (masked: {})", expiresIn, mask(token));
            return token;

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Không thể kết nối đến SePay eInvoice để lấy token: {}", ex.getMessage());
            throw new RuntimeException("Lỗi kết nối máy chủ SePay eInvoice: " + ex.getMessage(), ex);
        }
    }

    private synchronized HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return httpClient;
    }

    private String mask(String str) {
        if (str == null || str.length() <= 8) return "****";
        return str.substring(0, 4) + "..." + str.substring(str.length() - 4);
    }

    private Credentials resolveCredentials(BillingSetting settings, boolean production) {
        String configuredId = production ? settings.getProdClientId() : settings.getEinvoiceClientId();
        String configuredSecret = production ? settings.getProdClientSecret() : settings.getEinvoiceClientSecret();
        String fallbackId = production ? productionClientId : sandboxClientId;
        String fallbackSecret = production ? productionClientSecret : sandboxClientSecret;
        return new Credentials(
                hasText(configuredId) ? configuredId : fallbackId,
                hasText(configuredSecret) ? configuredSecret : fallbackSecret
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Credentials(String clientId, String clientSecret) {}
}
