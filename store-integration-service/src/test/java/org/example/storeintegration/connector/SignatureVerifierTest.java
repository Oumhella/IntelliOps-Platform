package org.example.storeintegration.connector;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class SignatureVerifierTest {
    private static final String MESSAGE = "The quick brown fox jumps over the lazy dog";

    @Test
    void verifiesKnownSha256HmacInHexAndBase64() {
        String hex = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
        String base64 = "97yD9DBThCSxMpjmqm+xQ+9NWaFJRhdZl0edvC0aPNg=";

        assertThat(SignatureVerifier.hexHmacSha256("key", MESSAGE, hex)).isTrue();
        assertThat(SignatureVerifier.base64HmacSha256("key", MESSAGE.getBytes(StandardCharsets.UTF_8), base64)).isTrue();
        assertThat(SignatureVerifier.hexHmacSha256("key", MESSAGE, hex.substring(2))).isFalse();
        assertThat(SignatureVerifier.base64HmacSha256("key", MESSAGE.getBytes(StandardCharsets.UTF_8), "not-base64")).isFalse();
    }
}
