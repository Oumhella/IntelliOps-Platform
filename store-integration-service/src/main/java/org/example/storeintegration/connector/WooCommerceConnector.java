package org.example.storeintegration.connector;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.storeintegration.domain.StorePlatform;
import org.example.storeintegration.dto.IntegrationDtos.ExternalProduct;
import org.example.storeintegration.security.CredentialCipher.StoreCredentials;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WooCommerceConnector implements StoreConnector {
    private final RestClient.Builder restClientBuilder;
    @Override public StorePlatform platform() { return StorePlatform.WOOCOMMERCE; }
    @Override public void verifyConnection(URI storeUrl, StoreCredentials credentials) {
        JsonNode response = request(storeUrl, credentials).get().uri("/wp-json/wc/v3/system_status").retrieve().body(JsonNode.class);
        if (response == null || response.isMissingNode()) throw new IllegalStateException("WooCommerce verification failed.");
    }
    @Override public List<ExternalProduct> listProducts(URI storeUrl, StoreCredentials credentials) {
        JsonNode products = request(storeUrl, credentials).get().uri("/wp-json/wc/v3/products?per_page=100").retrieve().body(JsonNode.class);
        List<ExternalProduct> result = new ArrayList<>();
        if (products == null || !products.isArray()) return result;
        for (JsonNode product : products) {
            String productId = product.path("id").asText();
            JsonNode variations = product.path("variations");
            if (variations.isArray() && !variations.isEmpty()) {
                for (JsonNode variation : variations) result.add(new ExternalProduct(productId, variation.asText(), product.path("sku").asText(""), product.path("name").asText() + " — variation " + variation.asText()));
            } else result.add(new ExternalProduct(productId, productId, product.path("sku").asText(""), product.path("name").asText()));
        }
        return result;
    }
    @Override public void registerOrderWebhook(URI storeUrl, StoreCredentials credentials, String deliveryUrl) {
        for (String topic : List.of("order.created", "order.updated")) {
            JsonNode response = request(storeUrl, credentials).post().uri("/wp-json/wc/v3/webhooks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", "IntelliOps " + topic, "topic", topic, "delivery_url", deliveryUrl,
                            "secret", credentials.webhookSecret(), "status", "active"))
                    .retrieve().body(JsonNode.class);
            if (response == null || response.path("id").asLong(0) <= 0) throw new IllegalStateException("WooCommerce webhook registration failed for " + topic + ".");
        }
    }
    public boolean validWebhook(StoreCredentials credentials, byte[] payload, String signature) {
        return credentials.webhookSecret() != null && SignatureVerifier.base64HmacSha256(credentials.webhookSecret(), payload, signature);
    }
    private RestClient request(URI storeUrl, StoreCredentials credentials) {
        return restClientBuilder.clone().baseUrl(storeUrl.toString()).defaultHeaders(headers -> headers.setBasicAuth(credentials.consumerKey(), credentials.consumerSecret())).build();
    }
}
