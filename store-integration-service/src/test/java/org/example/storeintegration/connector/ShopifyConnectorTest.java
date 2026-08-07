package org.example.storeintegration.connector;

import org.example.storeintegration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;

class ShopifyConnectorTest {
    private final ShopifyConnector connector = new ShopifyConnector(
            new IntegrationProperties("https://api.example.com", "https://app.example.com", false, 60, "MAD",
                    new IntegrationProperties.Shopify("client", "shopify-secret", "read_orders", "2026-07"), "unused"),
            RestClient.builder());

    @Test
    void verifiesSortedOAuthParametersAndRejectsAmbiguousDuplicates() {
        LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("timestamp", "1337178173");
        query.add("state", "nonce");
        query.add("hmac", "873d9573879d25f8bee6bf93578582cc0e7fc90518323b6edd09b4dc214c1811");
        query.add("shop", "demo-shop.myshopify.com");
        query.add("host", "encoded-host");
        query.add("code", "abc");

        assertThat(connector.validOAuthHmac(query)).isTrue();
        query.add("code", "second-value");
        assertThat(connector.validOAuthHmac(query)).isFalse();
    }
}
