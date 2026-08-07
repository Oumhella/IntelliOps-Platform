package org.example.storeintegration.connector;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.example.storeintegration.config.IntegrationProperties;
import org.example.storeintegration.domain.StorePlatform;
import org.example.storeintegration.dto.IntegrationDtos.ExternalProduct;
import org.example.storeintegration.security.CredentialCipher.StoreCredentials;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShopifyConnector implements StoreConnector {
    private final IntegrationProperties properties;
    private final RestClient.Builder restClientBuilder;
    @Override public StorePlatform platform() { return StorePlatform.SHOPIFY; }

    public String authorizationUrl(URI store, String callbackUrl, String state) {
        return UriComponentsBuilder.fromUri(store).path("/admin/oauth/authorize")
                .queryParam("client_id", properties.shopify().clientId())
                .queryParam("scope", properties.shopify().scopes())
                .queryParam("redirect_uri", callbackUrl).queryParam("state", state).build().encode().toUriString();
    }

    public boolean validOAuthHmac(MultiValueMap<String, String> query) {
        String provided = query.getFirst("hmac");
        if (query.values().stream().anyMatch(values -> values == null || values.size() != 1)) return false;
        String canonical = query.entrySet().stream().filter(entry -> !"hmac".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getKey() + "=" + entry.getValue().get(0)).reduce((a, b) -> a + "&" + b).orElse("");
        return provided != null && SignatureVerifier.hexHmacSha256(properties.shopify().clientSecret(), canonical, provided);
    }

    public String exchangeCode(URI store, String code) {
        JsonNode response = restClientBuilder.clone().build().post().uri(store.resolve("/admin/oauth/access_token"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", properties.shopify().clientId(), "client_secret", properties.shopify().clientSecret(), "code", code))
                .retrieve().body(JsonNode.class);
        String token = response == null ? null : response.path("access_token").asText(null);
        if (token == null || token.isBlank()) throw new IllegalStateException("Shopify did not return an access token.");
        return token;
    }

    @Override public void verifyConnection(URI storeUrl, StoreCredentials credentials) {
        JsonNode response = graphql(storeUrl, credentials.accessToken(), "query { shop { id name } }", Map.of());
        if (response.path("data").path("shop").path("id").asText().isBlank()) throw new IllegalStateException("Shopify shop verification failed.");
    }

    @Override public List<ExternalProduct> listProducts(URI storeUrl, StoreCredentials credentials) {
        String query = "query { products(first: 100) { edges { node { id title variants(first: 100) { edges { node { id title sku } } } } } } }";
        JsonNode edges = graphql(storeUrl, credentials.accessToken(), query, Map.of()).path("data").path("products").path("edges");
        List<ExternalProduct> result = new ArrayList<>();
        for (JsonNode productEdge : edges) {
            JsonNode product = productEdge.path("node");
            for (JsonNode variantEdge : product.path("variants").path("edges")) {
                JsonNode variant = variantEdge.path("node");
                result.add(new ExternalProduct(idTail(product.path("id").asText()), idTail(variant.path("id").asText()),
                        variant.path("sku").asText(""), product.path("title").asText() + " — " + variant.path("title").asText()));
            }
        }
        return result;
    }

    @Override public void registerOrderWebhook(URI storeUrl, StoreCredentials credentials, String deliveryUrl) {
        String mutation = "mutation CreateWebhook($topic: WebhookSubscriptionTopic!, $uri: URL!) { webhookSubscriptionCreate(topic: $topic, webhookSubscription: {uri: $uri, format: JSON}) { webhookSubscription { id } userErrors { field message } } }";
        for (String topic : List.of("ORDERS_CREATE", "ORDERS_UPDATED", "ORDERS_CANCELLED")) {
            JsonNode payload = graphql(storeUrl, credentials.accessToken(), mutation, Map.of("topic", topic, "uri", deliveryUrl));
            JsonNode result = payload.path("data").path("webhookSubscriptionCreate");
            if (result.path("webhookSubscription").path("id").asText().isBlank()) {
                throw new IllegalStateException("Shopify webhook registration failed for " + topic + ": " + result.path("userErrors"));
            }
        }
    }

    public boolean validWebhook(byte[] payload, String hmac) {
        return properties.shopifyConfigured() && SignatureVerifier.base64HmacSha256(properties.shopify().clientSecret(), payload, hmac);
    }

    private JsonNode graphql(URI storeUrl, String token, String query, Map<String, ?> variables) {
        URI endpoint = storeUrl.resolve("/admin/api/" + properties.shopify().apiVersion() + "/graphql.json");
        JsonNode response = restClientBuilder.clone().build().post().uri(endpoint).header("X-Shopify-Access-Token", token)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("query", query, "variables", variables)).retrieve().body(JsonNode.class);
        if (response == null || response.has("errors")) throw new IllegalStateException("Shopify Admin API rejected the request.");
        return response;
    }

    private String idTail(String gid) { int slash = gid.lastIndexOf('/'); return slash < 0 ? gid : gid.substring(slash + 1); }
}
