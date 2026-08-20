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
                        {"data":{"productVariants":{"nodes":[{"id":"gid://shopify/ProductVariant/20","title":"Blue","sku":"BOARD-BLUE","price":"249.90","inventoryQuantity":14,"inventoryItem":{"tracked":true},"product":{"id":"gid://shopify/Product/10","title":"Board"}}],"pageInfo":{"hasNextPage":false,"endCursor":null}}}}
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

    @Test
    void reconcilesExistingWebhookSubscriptionsToCurrentPublicUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyConnector webhookConnector = new ShopifyConnector(
                new IntegrationProperties("https://api.example.com", "https://app.example.com", false, 60, "MAD",
                        new IntegrationProperties.Shopify("client", "secret", "read_orders,read_products", "2026-07"),
                        "unused"), builder);
        String endpoint = "https://demo.myshopify.com/admin/api/2026-07/graphql.json";
        server.expect(requestTo(endpoint)).andRespond(withSuccess("""
                {"data":{"webhookSubscriptions":{"nodes":[
                  {"id":"gid://shopify/WebhookSubscription/1","topic":"ORDERS_CREATE","uri":"https://old.example/api/create"},
                  {"id":"gid://shopify/WebhookSubscription/2","topic":"ORDERS_UPDATED","uri":"https://old.example/api/update"},
                  {"id":"gid://shopify/WebhookSubscription/3","topic":"ORDERS_CANCELLED","uri":"https://old.example/api/cancel"}
                ]}}}
                """, MediaType.APPLICATION_JSON));
        for (int id = 1; id <= 3; id++) {
            final int subscriptionId = id;
            server.expect(requestTo(endpoint)).andRespond(withSuccess("""
                    {"data":{"webhookSubscriptionUpdate":{"webhookSubscription":{"id":"gid://shopify/WebhookSubscription/%d","uri":"https://current.example/webhook"},"userErrors":[]}}}
                    """.formatted(subscriptionId), MediaType.APPLICATION_JSON));
        }

        webhookConnector.registerOrderWebhook(URI.create("https://demo.myshopify.com"),
                org.example.storeintegration.security.CredentialCipher.StoreCredentials.shopify("token"),
                "https://current.example/webhook");

        server.verify();
    }
}
