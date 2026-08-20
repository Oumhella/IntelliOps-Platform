package org.example.storeintegration.connector;

import org.example.storeintegration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    @Test
    void listsRealVariantPriceAndSellableInventory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyConnector productConnector = new ShopifyConnector(
                new IntegrationProperties("https://api.example.com", "https://app.example.com", false, 60, "MAD",
                        new IntegrationProperties.Shopify("client", "secret", "read_orders,read_products", "2026-07"),
                        "unused"), builder);
        server.expect(requestTo("https://demo.myshopify.com/admin/api/2026-07/graphql.json"))
                .andRespond(withSuccess("""
                        {"data":{"products":{"edges":[{"node":{"id":"gid://shopify/Product/10","title":"Board","variants":{"edges":[{"node":{"id":"gid://shopify/ProductVariant/20","title":"Blue","sku":"BOARD-BLUE","price":"249.90","inventoryQuantity":14}}]}}}]}}}
                        """, MediaType.APPLICATION_JSON));

        var products = productConnector.listProducts(URI.create("https://demo.myshopify.com"),
                org.example.storeintegration.security.CredentialCipher.StoreCredentials.shopify("token"));

        assertThat(products).singleElement().satisfies(product -> {
            assertThat(product.productId()).isEqualTo("10");
            assertThat(product.variantId()).isEqualTo("20");
            assertThat(product.salePrice()).isEqualByComparingTo(new BigDecimal("249.90"));
            assertThat(product.availableQuantity()).isEqualTo(14);
        });
        server.verify();
    }
}
