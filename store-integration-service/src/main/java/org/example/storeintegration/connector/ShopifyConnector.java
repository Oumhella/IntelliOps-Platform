package org.example.storeintegration.connector;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyConnector implements StoreConnector {
    private final IntegrationProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public StorePlatform platform() {
        return StorePlatform.SHOPIFY;
    }

    public String authorizationUrl(URI store, String callbackUrl, String state) {
        return UriComponentsBuilder.fromUri(store).path("/admin/oauth/authorize")
                .queryParam("client_id", properties.shopify().clientId())
                .queryParam("scope", properties.shopify().scopes())
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    public boolean validOAuthHmac(MultiValueMap<String, String> query) {
        String provided = query.getFirst("hmac");
        if (query.values().stream().anyMatch(values -> values == null || values.size() != 1))
            return false;
        String canonical = query.entrySet().stream().filter(entry -> !"hmac".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getKey() + "=" + entry.getValue().get(0)).reduce((a, b) -> a + "&" + b).orElse("");
        return provided != null
                && SignatureVerifier.hexHmacSha256(properties.shopify().clientSecret(), canonical, provided);
    }

    public record ExchangeResult(String accessToken, String refreshToken, Long expiresIn) {
    }

    public ExchangeResult exchangeCode(URI store, String code) {
        URI endpoint = store.resolve("/admin/oauth/access_token");
        log.debug("exchangeCode: POST {} for store={}", endpoint, store);
        JsonNode response = restClientBuilder.clone().build().post().uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", properties.shopify().clientId(), "client_secret",
                        properties.shopify().clientSecret(), "code", code, "expiring", 1))
                .retrieve().body(JsonNode.class);
        if (response == null)
            throw new IllegalStateException("Shopify did not return a token response.");
        String token = response.path("access_token").asText(null);
        String refresh = response.path("refresh_token").asText(null);
        Long expires = response.has("expires_in") && !response.get("expires_in").isNull()
                ? response.get("expires_in").asLong()
                : null;
        if (token == null || token.isBlank())
            throw new IllegalStateException("Shopify did not return an access token.");
        log.debug("exchangeCode: token received (hasRefresh={}, expiresIn={})", refresh != null, expires);
        return new ExchangeResult(token, refresh, expires);
    }

    public ExchangeResult refreshAccessToken(URI store, String refreshToken) {
        JsonNode response = restClientBuilder.clone().build().post().uri(store.resolve("/admin/oauth/access_token"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", properties.shopify().clientId(), "client_secret",
                        properties.shopify().clientSecret(),
                        "grant_type", "refresh_token", "refresh_token", refreshToken))
                .retrieve().body(JsonNode.class);
        if (response == null)
            throw new IllegalStateException("Shopify did not return a token response on refresh.");
        String token = response.path("access_token").asText(null);
        String refresh = response.path("refresh_token").asText(null);
        Long expires = response.has("expires_in") && !response.get("expires_in").isNull()
                ? response.get("expires_in").asLong()
                : null;
        if (token == null || token.isBlank())
            throw new IllegalStateException("Shopify did not return an access token on refresh.");
        return new ExchangeResult(token, refresh, expires);
    }

    @Override
    public void verifyConnection(URI storeUrl, StoreCredentials credentials) {
        JsonNode response = graphql(storeUrl, credentials, "query { shop { id name } }", Map.of());
        if (response.path("data").path("shop").path("id").asText().isBlank())
            throw new IllegalStateException("Shopify shop verification failed.");
    }

    @Override
    public List<ExternalProduct> listProducts(URI storeUrl, StoreCredentials credentials) {
        String query = "query { products(first: 100) { edges { node { id title variants(first: 100) { edges { node { id title sku } } } } } } }";
        JsonNode edges = graphql(storeUrl, credentials, query, Map.of()).path("data").path("products").path("edges");
        List<ExternalProduct> result = new ArrayList<>();
        for (JsonNode productEdge : edges) {
            JsonNode product = productEdge.path("node");
            for (JsonNode variantEdge : product.path("variants").path("edges")) {
                JsonNode variant = variantEdge.path("node");
                result.add(new ExternalProduct(idTail(product.path("id").asText()), idTail(variant.path("id").asText()),
                        variant.path("sku").asText(""),
                        product.path("title").asText() + " — " + variant.path("title").asText()));
            }
        }
        return result;
    }

    @Override
    public void registerOrderWebhook(URI storeUrl, StoreCredentials credentials, String deliveryUrl) {
        String mutation = "mutation CreateWebhook($topic: WebhookSubscriptionTopic!, $subscription: WebhookSubscriptionInput!) { webhookSubscriptionCreate(topic: $topic, webhookSubscription: $subscription) { webhookSubscription { id } userErrors { field message } } }";
        List<String> failedTopics = new ArrayList<>();
        List<String> protectedDataTopics = new ArrayList<>();

        for (String topic : List.of("ORDERS_CREATE", "ORDERS_UPDATED", "ORDERS_CANCELLED")) {
            Map<String, Object> subscription = Map.of("callbackUrl", deliveryUrl, "format", "JSON");
            JsonNode payload = graphql(storeUrl, credentials, mutation, Map.of("topic", topic, "subscription", subscription));
            JsonNode result = payload.path("data").path("webhookSubscriptionCreate");
            if (result.path("webhookSubscription").path("id").asText().isBlank()) {
                Map<String, Object> fallbackSubscription = Map.of("uri", deliveryUrl, "format", "JSON");
                payload = graphql(storeUrl, credentials, mutation, Map.of("topic", topic, "subscription", fallbackSubscription));
                result = payload.path("data").path("webhookSubscriptionCreate");
                if (result.path("webhookSubscription").path("id").asText().isBlank()) {
                    String userErrors = result.path("userErrors").toString();
                    if (userErrors.contains("protected customer data")) {
                        protectedDataTopics.add(topic);
                    } else if (userErrors.contains("already been taken")) {
                        log.info("registerOrderWebhook: Webhook subscription for topic {} already exists.", topic);
                    } else {
                        failedTopics.add(topic + ": " + userErrors);
                    }
                }
            }
        }

        if (!protectedDataTopics.isEmpty()) {
            throw new IllegalStateException("App requires Protected Customer Data approval for Order webhooks (" + String.join(", ", protectedDataTopics) + "). Enable access in Shopify Partner Dashboard > API Access > Protected customer data.");
        }
        if (!failedTopics.isEmpty()) {
            throw new IllegalStateException("Shopify webhook registration failed: " + String.join("; ", failedTopics));
        }
    }

    public boolean validWebhook(byte[] payload, String hmac) {
        return properties.shopifyConfigured()
                && SignatureVerifier.base64HmacSha256(properties.shopify().clientSecret(), payload, hmac);
    }

    private JsonNode graphql(URI storeUrl, StoreCredentials credentials, String query, Map<String, ?> variables) {
        URI endpoint = storeUrl.resolve("/admin/api/" + properties.shopify().apiVersion() + "/graphql.json");
        log.debug("graphql: POST {} (query length={})", endpoint, query.length());
        try {
            JsonNode response = restClientBuilder.clone().build().post().uri(endpoint)
                    .header("X-Shopify-Access-Token", credentials.accessToken())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("query", query, "variables", variables))
                    .retrieve().body(JsonNode.class);
            if (response == null || response.has("errors")) {
                log.warn("graphql: Shopify Admin API error response: {}", response);
                throw new IllegalStateException("Shopify Admin API rejected the request.");
            }
            return response;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            int status = ex.getRawStatusCode();
            String body = ex.getResponseBodyAsString();
            log.warn("graphql: Shopify responded with status={}, body={}", status, body);
            if ((status == 401 || status == 403) && credentials.refreshToken() != null && !credentials.refreshToken().isBlank()) {
                try {
                    log.info("graphql: Shopify access token expired or invalid (status={}), attempting refresh...", status);
                    ExchangeResult refreshed = refreshAccessToken(storeUrl, credentials.refreshToken());
                    throw new TokenRefreshedException("Access token refreshed", refreshed);
                } catch (TokenRefreshedException tre) {
                    throw tre;
                } catch (Exception refreshEx) {
                    log.warn("graphql: Token refresh failed: {}", refreshEx.getMessage());
                }
            }
            throw ex;
        }
    }

    private String idTail(String gid) {
        int slash = gid.lastIndexOf('/');
        return slash < 0 ? gid : gid.substring(slash + 1);
    }
}
