package org.example.storeintegration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.storeintegration.config.IntegrationProperties;
import org.example.storeintegration.security.CredentialCipher.StoreCredentials;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {
    private final CredentialCipher cipher = new CredentialCipher(properties(), new ObjectMapper());

    @Test
    void roundTripsWithoutExposingPlaintextAndRejectsTampering() {
        StoreCredentials credentials = StoreCredentials.woocommerce("ck_private", "cs_private", "webhook-secret");
        String encrypted = cipher.encrypt(credentials);

        assertThat(encrypted).startsWith("v1.").doesNotContain("ck_private", "cs_private", "webhook-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo(credentials);

        String[] parts = encrypted.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[2]);
        payload[0] = (byte) (payload[0] ^ 0xFF);
        String tampered = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    private IntegrationProperties properties() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new IntegrationProperties("https://api.example.com", "https://app.example.com", false, 60, "MAD", null, key);
    }
}
