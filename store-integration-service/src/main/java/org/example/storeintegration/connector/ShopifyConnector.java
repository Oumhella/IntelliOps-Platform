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
import java.util.HashMap;
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
        String query = "query ProductVariants($after: String) { productVariants(first: 250, after: $after) { nodes { id title sku price inventoryQuantity inventoryItem { tracked } product { id title } } pageInfo { hasNextPage endCursor } } }";
        List<ExternalProduct> result = new ArrayList<>();
        String after = null;
        do {
            Map<String, Object> variables = new HashMap<>();
            variables.put("after", after);
            JsonNode connection = graphql(storeUrl, credentials, query, variables)
                    .path("data").path("productVariants");
            for (JsonNode variant : connection.path("nodes")) {
                JsonNode product = variant.path("product");
                String name = product.path("title").asText() + " — " + variant.path("title").asText();
                Integer quantity = variant.path("inventoryItem").path("tracked").asBoolean(false)
                        ? nullableNonNegativeInt(variant, "inventoryQuantity") : null;
                result.add(new ExternalProduct(
                        idTail(product.path("id").asText()), idTail(variant.path("id").asText()),
                        variant.path("sku").asText(""), name, decimalOrNull(variant, "price"),
                        quantity));
            }
            boolean hasNextPage = connection.path("pageInfo").path("hasNextPage").asBoolean(false);
            after = hasNextPage ? connection.path("pageInfo").path("endCursor").asText(null) : null;
        } while (after != null && !after.isBlank());
        return result;
    }

    private java.math.BigDecimal decimalOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) return null;
        try {
            return new java.math.BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer nullableNonNegativeInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? Math.max(0, value.asInt()) : null;
    }

    @Override
    public void registerOrderWebhook(URI storeUrl, StoreCredentials credentials, String deliveryUrl) {
        String listQuery = "query { webhookSubscriptions(first: 250) { nodes { id topic uri } } }";
        JsonNode subscriptions = graphql(storeUrl, credentials, listQuery, Map.of())
                .path("data").path("webhookSubscriptions").path("nodes");
        List<String> failedTopics = new ArrayList<>();
        List<String> protectedDataTopics = new ArrayList<>();

        for (String topic : List.of("ORDERS_CREATE", "ORDERS_UPDATED", "ORDERS_CANCELLED")) {
            List<JsonNode> topicSubscriptions = new ArrayList<>();
            for (JsonNode subscription : subscriptions) {
                if (topic.equals(subscription.path("topic").asText())) topicSubscriptions.add(subscription);
            }
            JsonNode current = topicSubscriptions.stream()
                    .filter(subscription -> deliveryUrl.equals(subscription.path("uri").asText()))
                    .findFirst().orElse(null);
            try {
                if (current == null && topicSubscriptions.isEmpty()) {
                    createWebhook(storeUrl, credentials, topic, deliveryUrl);
                    log.info("registerOrderWebhook: created {} at {}", topic, deliveryUrl);
                } else if (current == null) {
                    current = topicSubscriptions.remove(0);
                    updateWebhook(storeUrl, credentials, current.path("id").asText(), deliveryUrl);
                    log.info("registerOrderWebhook: updated {} callback to {}", topic, deliveryUrl);
                } else {
                    topicSubscriptions.remove(current);
                    log.info("registerOrderWebhook: verified {} callback at {}", topic, deliveryUrl);
                }
                for (JsonNode stale : topicSubscriptions) {
                    deleteWebhook(storeUrl, credentials, stale.path("id").asText());
                    log.info("registerOrderWebhook: removed stale {} callback {}", topic, stale.path("uri").asText());
                }
            } catch (IllegalStateException exception) {
                if (exception.getMessage().toLowerCase().contains("protected customer data")) {
                    protectedDataTopics.add(topic);
                } else {
                    failedTopics.add(topic + ": " + exception.getMessage());
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

    private void createWebhook(URI storeUrl, StoreCredentials credentials, String topic, String deliveryUrl) {
        String mutation = "mutation CreateWebhook($topic: WebhookSubscriptionTopic!, $subscription: WebhookSubscriptionInput!) { webhookSubscriptionCreate(topic: $topic, webhookSubscription: $subscription) { webhookSubscription { id uri } userErrors { field message } } }";
        JsonNode result = graphql(storeUrl, credentials, mutation,
                Map.of("topic", topic, "subscription", Map.of("uri", deliveryUrl, "format", "JSON")))
                .path("data").path("webhookSubscriptionCreate");
        requireWebhookMutation(result, "create");
    }

    private void updateWebhook(URI storeUrl, StoreCredentials credentials, String id, String deliveryUrl) {
        String mutation = "mutation UpdateWebhook($id: ID!, $subscription: WebhookSubscriptionInput!) { webhookSubscriptionUpdate(id: $id, webhookSubscription: $subscription) { webhookSubscription { id uri } userErrors { field message } } }";
        JsonNode result = graphql(storeUrl, credentials, mutation,
                Map.of("id", id, "subscription", Map.of("uri", deliveryUrl, "format", "JSON")))
                .path("data").path("webhookSubscriptionUpdate");
        requireWebhookMutation(result, "update");
    }

    private void deleteWebhook(URI storeUrl, StoreCredentials credentials, String id) {
        String mutation = "mutation DeleteWebhook($id: ID!) { webhookSubscriptionDelete(id: $id) { deletedWebhookSubscriptionId userErrors { field message } } }";
        JsonNode result = graphql(storeUrl, credentials, mutation, Map.of("id", id))
                .path("data").path("webhookSubscriptionDelete");
        if (result.path("deletedWebhookSubscriptionId").asText().isBlank()) {
            throw new IllegalStateException("Shopify webhook delete failed: " + result.path("userErrors"));
        }
    }

    private void requireWebhookMutation(JsonNode result, String operation) {
        if (result.path("webhookSubscription").path("id").asText().isBlank()) {
            throw new IllegalStateException("Shopify webhook " + operation + " failed: " + result.path("userErrors"));
        }
    }

    public boolean validWebhook(byte[] payload, String hmac) {
        return properties.shopifyConfigured()
                && SignatureVerifier.base64HmacSha256(properties.shopify().clientSecret(), payload, hmac);
    }

    /**
     * Loads the full order from Admin REST API. Shopify order webhooks often redact
     * protected customer fields (email, phone, street address); the Admin API returns
     * them when the app has the required access and scopes.
     */
    public JsonNode fetchOrder(URI storeUrl, StoreCredentials credentials, String orderId) {
        if (orderId == null || orderId.isBlank() || !orderId.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Shopify order id must be numeric.");
        }
        URI endpoint = storeUrl.resolve("/admin/api/" + properties.shopify().apiVersion() + "/orders/" + orderId + ".json");
        log.debug("fetchOrder: GET {}", endpoint);
        JsonNode response = adminGet(storeUrl, credentials, endpoint);
        JsonNode order = response.path("order");
        if (!order.isObject() || order.path("id").isMissingNode()) {
            throw new IllegalStateException("Shopify Admin API did not return order " + orderId + ".");
        }
        return order;
    }

    private JsonNode adminGet(URI storeUrl, StoreCredentials credentials, URI endpoint) {
        try {
            JsonNode response = restClientBuilder.clone().build().get().uri(endpoint)
                    .header("X-Shopify-Access-Token", credentials.accessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
            if (response == null) {
                throw new IllegalStateException("Shopify Admin API returned an empty response.");
            }
            return response;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            maybeRefreshAndRethrow(storeUrl, credentials, ex);
            throw ex;
        }
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
            maybeRefreshAndRethrow(storeUrl, credentials, ex);
            throw ex;
        }
    }

    private void maybeRefreshAndRethrow(URI storeUrl, StoreCredentials credentials,
            org.springframework.web.client.RestClientResponseException ex) {
        int status = ex.getRawStatusCode();
        String body = ex.getResponseBodyAsString();
        log.warn("Shopify Admin API responded with status={}, body={}", status, body);
        if ((status == 401 || status == 403) && credentials.refreshToken() != null && !credentials.refreshToken().isBlank()) {
            try {
                log.info("Shopify access token expired or invalid (status={}), attempting refresh...", status);
                ExchangeResult refreshed = refreshAccessToken(storeUrl, credentials.refreshToken());
                throw new TokenRefreshedException("Access token refreshed", refreshed);
            } catch (TokenRefreshedException tre) {
                throw tre;
            } catch (Exception refreshEx) {
                log.warn("Shopify token refresh failed: {}", refreshEx.getMessage());
            }
        }
    }

    private String idTail(String gid) {
        int slash = gid.lastIndexOf('/');
        return slash < 0 ? gid : gid.substring(slash + 1);
    }
}
