package org.example.storeintegration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.storeintegration.config.IntegrationProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CredentialCipher {
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(IntegrationProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            byte[] decoded = Base64.getDecoder().decode(properties.credentialsMasterKey());
            if (decoded.length != 32) throw new IllegalArgumentException("key must contain 32 bytes");
            this.key = new SecretKeySpec(decoded, "AES");
        } catch (RuntimeException exception) {
            throw new IllegalStateException("integration.credentials-master-key must be a Vault-backed Base64 AES-256 key", exception);
        }
    }

    public String encrypt(StoreCredentials credentials) {
        try {
            byte[] iv = new byte[IV_BYTES]; secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(credentials));
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception exception) { throw new IllegalStateException("Store credentials could not be encrypted", exception); }
    }

    public StoreCredentials decrypt(String value) {
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("unsupported credential envelope");
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return objectMapper.readValue(cipher.doFinal(encrypted), StoreCredentials.class);
        } catch (Exception exception) { throw new IllegalStateException("Stored credentials could not be decrypted", exception); }
    }

    public record StoreCredentials(String accessToken, String consumerKey, String consumerSecret, String webhookSecret) {
        public static StoreCredentials shopify(String accessToken) { return new StoreCredentials(accessToken, null, null, null); }
        public static StoreCredentials woocommerce(String key, String secret, String webhookSecret) { return new StoreCredentials(null, key, secret, webhookSecret); }
    }
}
