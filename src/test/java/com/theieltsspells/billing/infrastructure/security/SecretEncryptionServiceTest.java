package com.theieltsspells.billing.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretEncryptionServiceTest {

    private SecretEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new SecretEncryptionService();
    }

    @Test
    @DisplayName("1. Encrypt and decrypt roundtrip preserves exact secret content")
    void testEncryptDecrypt_Roundtrip() {
        String originalSecret = "d9f2e85f5151eb2f07d875a23113ecd0";
        String encrypted = encryptionService.encrypt(originalSecret);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).isNotEqualTo(originalSecret);

        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(originalSecret);
    }

    @Test
    @DisplayName("2. AES-256-GCM produces randomized IV (different ciphertexts for identical plaintext)")
    void testEncrypt_RandomizedIV() {
        String secret = "super-confidential-sepay-secret-key-2026";
        String encrypted1 = encryptionService.encrypt(secret);
        String encrypted2 = encryptionService.encrypt(secret);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(secret);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(secret);
    }

    @Test
    @DisplayName("3. Transparent fallback: Legacy plaintext without enc:v1: prefix returns as-is on decrypt")
    void testDecrypt_LegacyPlaintextFallback() {
        String legacyPlaintext = "my-unencrypted-legacy-secret";
        String result = encryptionService.decrypt(legacyPlaintext);
        assertThat(result).isEqualTo(legacyPlaintext);
    }

    @Test
    @DisplayName("4. Double encryption prevention: Already encrypted text is not re-encrypted")
    void testEncrypt_AlreadyEncrypted_NoOp() {
        String secret = "sepay_webhook_secret_xyz";
        String encrypted = encryptionService.encrypt(secret);
        String reEncrypted = encryptionService.encrypt(encrypted);

        assertThat(reEncrypted).isEqualTo(encrypted);
    }

    @Test
    @DisplayName("5. Mask Client ID formats correctly")
    void testMaskClientId() {
        assertThat(encryptionService.maskClientId("EINV-TEST-EB0HM0MMQW9LMZHP")).isEqualTo("EINV-****MZHP");
        assertThat(encryptionService.maskClientId("PROD-12345678-ABCD")).isEqualTo("PROD-****ABCD");
        assertThat(encryptionService.maskClientId(null)).isEqualTo("");
        assertThat(encryptionService.maskClientId("short")).isEqualTo("********");
    }

    @Test
    @DisplayName("6. Mask Account Number formats correctly")
    void testMaskAccountNumber() {
        assertThat(encryptionService.maskAccountNumber("0987654321")).isEqualTo("******4321");
        assertThat(encryptionService.maskAccountNumber("1234")).isEqualTo("****");
        assertThat(encryptionService.maskAccountNumber(null)).isEqualTo("");
    }

    // =========================================================================
    // MANDATORY AUDIT TESTS: MASTER KEY FAIL-CLOSED
    // =========================================================================

    @Test
    @DisplayName("productionWithoutMasterKey_readinessFails: Production without master key fails validation and readiness")
    void productionWithoutMasterKey_readinessFails() {
        SecretEncryptionService prodService = new SecretEncryptionService(null, true);
        assertThat(prodService.isProductionEnvironment()).isTrue();
        assertThat(prodService.hasValidMasterKey()).isFalse();
        assertThat(prodService.isDevFallbackActive()).isFalse();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            prodService.validateProductionMasterKey();
        });
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            prodService.encrypt("my-secret");
        });
    }

    @Test
    @DisplayName("productionCannotUseDevFallback: In production, dev fallback key is rejected")
    void productionCannotUseDevFallback() {
        SecretEncryptionService prodServiceWithDevKey = new SecretEncryptionService(SecretEncryptionService.DEV_FALLBACK_KEY_VALUE, true);
        assertThat(prodServiceWithDevKey.hasValidMasterKey()).isFalse();
        assertThat(prodServiceWithDevKey.isDevFallbackActive()).isFalse();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            prodServiceWithDevKey.encrypt("my-secret");
        });

        SecretEncryptionService prodServiceWithShortKey = new SecretEncryptionService("too-short-key", true);
        assertThat(prodServiceWithShortKey.hasValidMasterKey()).isFalse();
    }

    @Test
    @DisplayName("wrongMasterKey_doesNotOverwriteExistingSecret: Wrong key fails decryption without overwriting existing stored secret")
    void wrongMasterKey_doesNotOverwriteExistingSecret() {
        String originalKey = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6";
        SecretEncryptionService serviceA = new SecretEncryptionService(originalKey, false);
        String secretPlain = "sepay-prod-client-secret-9999";
        String encryptedSecret = serviceA.encrypt(secretPlain);

        String wrongKey = "z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4";
        SecretEncryptionService serviceB = new SecretEncryptionService(wrongKey, false);

        assertThat(serviceB.canDecrypt(encryptedSecret)).isFalse();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            serviceB.decrypt(encryptedSecret);
        });

        assertThat(encryptedSecret).startsWith("enc:v1:");
        assertThat(serviceA.decrypt(encryptedSecret)).isEqualTo(secretPlain);
    }

    @Test
    @DisplayName("secretEncryptionUsesRandomNonce: AES-256-GCM uses a randomized nonce (IV) for every encryption")
    void secretEncryptionUsesRandomNonce() {
        String plain = "my-secure-token-123456";
        String enc1 = encryptionService.encrypt(plain);
        String enc2 = encryptionService.encrypt(plain);

        String iv1 = enc1.substring("enc:v1:".length()).split(":")[0];
        String iv2 = enc2.substring("enc:v1:".length()).split(":")[0];

        assertThat(iv1).isNotEqualTo(iv2);
    }

    @Test
    @DisplayName("samePlaintextProducesDifferentCiphertext: Repeated encryption of identical plaintext produces different ciphertexts")
    void samePlaintextProducesDifferentCiphertext() {
        String plain = "constant-plaintext-data";
        String enc1 = encryptionService.encrypt(plain);
        String enc2 = encryptionService.encrypt(plain);
        String enc3 = encryptionService.encrypt(plain);

        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(enc2).isNotEqualTo(enc3);
        assertThat(enc1).isNotEqualTo(enc3);
    }

    @Test
    @DisplayName("ciphertextStillDecryptsCorrectly: Different ciphertexts all decrypt back to the original plaintext")
    void ciphertextStillDecryptsCorrectly() {
        String plain = "special-payment-credential-secret";
        String enc1 = encryptionService.encrypt(plain);
        String enc2 = encryptionService.encrypt(plain);
        String enc3 = encryptionService.encrypt(plain);

        assertThat(encryptionService.decrypt(enc1)).isEqualTo(plain);
        assertThat(encryptionService.decrypt(enc2)).isEqualTo(plain);
        assertThat(encryptionService.decrypt(enc3)).isEqualTo(plain);
    }
}
