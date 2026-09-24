package com.theieltsspells.billing.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive credentials at rest (AES-256-GCM).
 * <p>
 * Secrets stored in the database are prefixed with {@code enc:v1:}.
 * Plaintext legacy secrets are decrypted transparently and re-encrypted on write.
 * <p>
 * Master Key Fail-Closed Policy:
 * In Production: SEPAY_MASTER_KEY or secret-management key is MANDATORY and must satisfy AES-256 (>= 32 chars).
 * Dev fallback is strictly prohibited in Production.
 */
@Slf4j
@Service
public class SecretEncryptionService {

    public static final String DEV_FALLBACK_KEY_VALUE = "the-ielts-spells-default-dev-master-key-2026";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String PREFIX = "enc:v1:";

    private final SecretKey masterKey;
    private final boolean devFallbackActive;
    private final boolean validMasterKey;
    private final boolean productionEnvironment;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService() {
        this(null, null, null);
    }

    @Autowired(required = false)
    public SecretEncryptionService(Environment environment) {
        this(environment, null, null);
    }

    /**
     * Testing constructor with custom key and forced environment mode.
     */
    public SecretEncryptionService(String customKey, Boolean forceProduction) {
        this(null, customKey, forceProduction);
    }

    /**
     * Package-private constructor for testing with custom pre-built SecretKey.
     */
    SecretEncryptionService(SecretKey customKey) {
        this.masterKey = customKey;
        this.devFallbackActive = false;
        this.validMasterKey = (customKey != null);
        this.productionEnvironment = false;
    }

    private SecretEncryptionService(Environment environment, String explicitKey, Boolean forceProduction) {
        this.productionEnvironment = forceProduction != null
                ? forceProduction
                : detectProduction(environment);

        String envKey = explicitKey;
        if (envKey == null || envKey.isBlank()) {
            envKey = System.getenv("SEPAY_MASTER_KEY");
            if (envKey == null || envKey.isBlank()) {
                envKey = System.getenv("APP_ENCRYPTION_KEY");
            }
        }

        // Validate key against AES-256 requirements:
        // A valid AES-256 master key must be non-empty, >= 32 characters, and not the dev fallback constant.
        boolean keyProvided = (envKey != null && !envKey.isBlank());
        boolean keyIsFallback = DEV_FALLBACK_KEY_VALUE.equals(envKey);
        boolean keyMeetsAes256 = keyProvided && !keyIsFallback && envKey.trim().length() >= 32;

        if (this.productionEnvironment) {
            if (!keyMeetsAes256) {
                log.error("[CRITICAL SECURITY ALERT] Production environment detected, but SEPAY_MASTER_KEY is not configured or does not meet AES-256 requirements (>= 32 chars). "
                        + "Dev fallback is strictly prohibited in Production. Production credential operations are BLOCKED.");
                this.masterKey = null;
                this.devFallbackActive = false;
                this.validMasterKey = false;
                return;
            }
            this.validMasterKey = true;
            this.devFallbackActive = false;
        } else {
            if (!keyMeetsAes256) {
                log.warn("[SECURITY WARNING] Neither SEPAY_MASTER_KEY nor APP_ENCRYPTION_KEY environment variable is set or key is < 32 chars. "
                        + "Using internal dev fallback master key for non-production environment. "
                        + "Ensure SEPAY_MASTER_KEY (>= 32 chars) is configured before deploying to Production!");
                envKey = DEV_FALLBACK_KEY_VALUE;
                this.devFallbackActive = true;
                this.validMasterKey = false;
            } else {
                this.devFallbackActive = false;
                this.validMasterKey = true;
            }
        }

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(envKey.getBytes(StandardCharsets.UTF_8));
            this.masterKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể khởi tạo khóa mã hóa AES: " + ex.getMessage(), ex);
        }
    }

    private static boolean detectProduction(Environment env) {
        if (env != null && (env.matchesProfiles("prod", "production"))) {
            return true;
        }
        String appEnv = System.getenv("APP_ENV");
        if ("prod".equalsIgnoreCase(appEnv) || "production".equalsIgnoreCase(appEnv)) {
            return true;
        }
        String springProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (springProfiles != null && (springProfiles.contains("prod") || springProfiles.contains("production"))) {
            return true;
        }
        String sysProp = System.getProperty("spring.profiles.active");
        return sysProp != null && (sysProp.contains("prod") || sysProp.contains("production"));
    }

    public boolean isProductionEnvironment() {
        return productionEnvironment;
    }

    public boolean hasValidMasterKey() {
        return validMasterKey && masterKey != null;
    }

    public boolean isDevFallbackActive() {
        return devFallbackActive;
    }

    public void validateProductionMasterKey() {
        if (productionEnvironment && (!hasValidMasterKey() || isDevFallbackActive())) {
            throw new IllegalStateException("Production master key (SEPAY_MASTER_KEY) is required and must meet AES-256 standards (>= 32 chars). Dev fallback cannot be used in Production.");
        }
    }

    /**
     * Mã hóa chuỗi plaintext sang định dạng AES-GCM base64 kèm prefix enc:v1:
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (isEncrypted(plaintext)) {
            return plaintext; // Already encrypted
        }

        validateProductionMasterKey();

        if (masterKey == null) {
            throw new IllegalStateException("Không thể mã hóa: Không có khóa master key hợp lệ.");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String cipherBase64 = Base64.getEncoder().encodeToString(ciphertext);

            return PREFIX + ivBase64 + ":" + cipherBase64;
        } catch (Exception ex) {
            log.error("Lỗi khi mã hóa dữ liệu nhạy cảm: {}", ex.getMessage());
            throw new IllegalStateException("Lỗi mã hóa dữ liệu: " + ex.getMessage(), ex);
        }
    }

    /**
     * Giải mã chuỗi đã mã hóa. Nếu là chuỗi plaintext cũ (không có prefix enc:v1:), trả về nguyên bản.
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (!isEncrypted(stored)) {
            return stored; // Plaintext fallback
        }

        validateProductionMasterKey();

        if (masterKey == null) {
            throw new IllegalStateException("Không thể giải mã: Không có khóa master key hợp lệ.");
        }

        try {
            String payload = stored.substring(PREFIX.length());
            String[] parts = payload.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Định dạng ciphertext không hợp lệ");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Lỗi khi giải mã credential: {}", ex.getMessage());
            throw new IllegalStateException("Không thể giải mã credential (sai khóa hoặc dữ liệu hỏng): " + ex.getMessage(), ex);
        }
    }

    /**
     * Kiểm tra xem ciphertext có thể giải mã được bằng master key hiện tại hay không.
     * Trả về false nếu sai khóa hoặc dữ liệu bị hỏng mà không quăng exception.
     */
    public boolean canDecrypt(String stored) {
        if (stored == null || stored.isBlank() || !isEncrypted(stored)) {
            return true;
        }
        if (masterKey == null) {
            return false;
        }
        try {
            decrypt(stored);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isEncrypted(String text) {
        return text != null && text.startsWith(PREFIX);
    }

    /**
     * Tạo chuỗi che giấu an toàn (masked) cho frontend.
     */
    public String maskClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "";
        }
        String trimmed = clientId.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        String prefix = trimmed.substring(0, Math.min(trimmed.indexOf('-') > 0 ? trimmed.indexOf('-') + 1 : 4, trimmed.length() - 4));
        String suffix = trimmed.substring(trimmed.length() - 4);
        return prefix + "****" + suffix;
    }

    public String maskAccountNumber(String accountNum) {
        if (accountNum == null || accountNum.isBlank()) {
            return "";
        }
        String trimmed = accountNum.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "******" + trimmed.substring(trimmed.length() - 4);
    }
}
