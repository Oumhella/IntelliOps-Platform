package org.example.storeintegration.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.exception.ConflictException;
import org.example.common.security.TenantContext;
import org.example.storeintegration.client.CoreOperationsClient;
import org.example.storeintegration.config.IntegrationProperties;
import org.example.storeintegration.connector.*;
import org.example.storeintegration.domain.*;
import org.example.storeintegration.dto.IntegrationDtos.*;
import org.example.storeintegration.entity.*;
import org.example.storeintegration.repository.*;
import org.example.storeintegration.security.CredentialCipher;
import org.example.storeintegration.security.CredentialCipher.StoreCredentials;
import org.example.storeintegration.security.ExternalUrlPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationService {
    private final IntegrationProperties properties;
    private final ExternalUrlPolicy urlPolicy;
    private final ShopifyConnector shopifyConnector;
    private final WooCommerceConnector wooCommerceConnector;
    private final ConnectorFactory connectorFactory;
    private final CredentialCipher credentialCipher;
    private final CoreOperationsClient coreClient;
    private final StoreConnectionRepository connectionRepository;
    private final ProductMappingRepository mappingRepository;
    private final OAuthStateRepository stateRepository;
    private final WebhookEventRepository eventRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CapabilitiesResponse capabilities() {
        boolean callbacks = properties.hasPublicCallbackUrl();
        return new CapabilitiesResponse(properties.shopifyConfigured(), callbacks, callbacks, "INTELLIOPS",
                callbacks ? "Verified order ingestion is available. IntelliOps remains inventory authority."
                        : "Configure an HTTPS public callback URL before connecting an external store.");
    }

    @Transactional
    public StartedAuthorization beginShopify(ConnectRequest request) {
        if (!properties.shopifyConfigured())
            throw new IllegalStateException("Shopify app credentials or HTTPS callback URL are not configured.");
        URI store = urlPolicy.requireShopifyStore(request.store());
        verifyLocation(TenantContext.requireEnterpriseId(), request.stockLocationId());
        StateToken state = createState(StorePlatform.SHOPIFY, request, store);
        String callback = publicUrl("/api/v1/integrations/oauth/shopify/callback");
        return new StartedAuthorization(
                new AuthorizationResponse(shopifyConnector.authorizationUrl(store, callback, state.raw()),
                        state.expiresAt()),
                state.raw());
    }

    @Transactional
    public AuthorizationResponse beginWooCommerce(ConnectRequest request) {
        requirePublicCallbacks();
        URI store = urlPolicy.requirePublicHttpsStore(request.store());
        verifyLocation(TenantContext.requireEnterpriseId(), request.stockLocationId());
        StateToken state = createState(StorePlatform.WOOCOMMERCE, request, store);
        String authorizationUrl = UriComponentsBuilder.fromUri(store).path("/wc-auth/v1/authorize")
                .queryParam("app_name", "IntelliOps").queryParam("scope", "read_write")
                .queryParam("user_id", state.raw())
                .queryParam("return_url", properties.frontendReturnUrl() + "?woocommerce_return=1")
                .queryParam("callback_url", publicUrl("/api/v1/integrations/oauth/woocommerce/callback"))
                .build().encode().toUriString();
        return new AuthorizationResponse(authorizationUrl, state.expiresAt());
    }

    @Transactional
    public StoreConnection completeShopify(MultiValueMap<String, String> query, String browserState) {
        log.debug("completeShopify: validating HMAC signature");
        if (!shopifyConnector.validOAuthHmac(query)) {
            log.warn("completeShopify: HMAC validation failed for shop={}", query.getFirst("shop"));
            throw new SecurityException("Invalid Shopify OAuth signature.");
        }
        String rawState = required(query.getFirst("state"), "OAuth state is required.");
        String code = required(query.getFirst("code"), "Shopify authorization code is required.");
        URI shop = urlPolicy.requireShopifyStore(query.getFirst("shop"));
        log.debug("completeShopify: looking up OAuth state for shop={}", shop);
        OAuthState state = requireState(rawState, StorePlatform.SHOPIFY);
        if (!state.getStoreUrl().equals(shop.toString())) {
            log.warn("completeShopify: shop mismatch. expected={}, received={}", state.getStoreUrl(), shop);
            throw new SecurityException("Shopify shop does not match the authorization request.");
        }
        log.debug("completeShopify: exchanging authorization code for tokens");
        var exchange = shopifyConnector.exchangeCode(shop, code);
        Long expiresAtEpoch = exchange.expiresIn() == null ? null
                : Instant.now().plusSeconds(exchange.expiresIn()).getEpochSecond();
        StoreCredentials credentials = StoreCredentials.shopify(exchange.accessToken(), exchange.refreshToken(),
                expiresAtEpoch);
        log.debug("completeShopify: verifying connection with Shopify Admin API");
        try {
            shopifyConnector.verifyConnection(shop, credentials);
        } catch (org.springframework.web.client.HttpClientErrorException forbidden) {
            // If Shopify rejects the provided token but we received a refresh token,
            // attempt refresh then retry once.
            String body = forbidden.getResponseBodyAsString();
            log.warn("completeShopify: initial verification failed ({}), body={}", forbidden.getStatusCode(), body);
            if (exchange.refreshToken() != null && body != null
                    && body.contains("Non-expiring access tokens are no longer accepted")) {
                log.info("completeShopify: refreshing access token");
                var refreshed = shopifyConnector.refreshAccessToken(shop, exchange.refreshToken());
                Long newExpires = refreshed.expiresIn() == null ? null
                        : Instant.now().plusSeconds(refreshed.expiresIn()).getEpochSecond();
                credentials = StoreCredentials.shopify(refreshed.accessToken(), refreshed.refreshToken(), newExpires);
                shopifyConnector.verifyConnection(shop, credentials);
            } else {
                throw forbidden;
            }
        }
        log.debug("completeShopify: saving connection and registering webhooks");
        StoreConnection connection = saveConnection(state, credentials);
        registerWebhook(connection, credentials);
        state.setConsumedAt(Instant.now());
        stateRepository.save(state);
        log.info("completeShopify: completed for shop={}, connectionId={}", shop, connection.getId());
        return connection;
    }

    @Transactional
    public StoreConnection completeWooCommerce(WooAuthorizationCallback callback) {
        OAuthState state = requireState(required(callback.state(), "WooCommerce state is required."),
                StorePlatform.WOOCOMMERCE);
        if (callback.consumerKey() == null || !callback.consumerKey().startsWith("ck_")
                || callback.consumerSecret() == null || !callback.consumerSecret().startsWith("cs_")
                || callback.keyPermissions() == null || !callback.keyPermissions().contains("write")) {
            throw new IllegalArgumentException("WooCommerce must grant read/write API credentials.");
        }
        URI store = urlPolicy.requirePublicHttpsStore(state.getStoreUrl());
        String webhookSecret = randomToken();
        StoreCredentials credentials = StoreCredentials.woocommerce(callback.consumerKey(), callback.consumerSecret(),
                webhookSecret);
        wooCommerceConnector.verifyConnection(store, credentials);
        StoreConnection connection = saveConnection(state, credentials);
        registerWebhook(connection, credentials);
        state.setConsumedAt(Instant.now());
        stateRepository.save(state);
        return connection;
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> connections() {
        return connectionRepository.findAllByEnterpriseIdOrderByCreatedAtDesc(TenantContext.requireEnterpriseId())
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ExternalProduct> externalProducts(Long connectionId) {
        StoreConnection connection = requireConnection(connectionId);
        if (connection.getStatus() == ConnectionStatus.DISCONNECTED)
            throw new IllegalStateException("The store is disconnected.");
        URI store = connection.getPlatform() == StorePlatform.SHOPIFY
                ? urlPolicy.requireShopifyStore(connection.getStoreUrl())
                : urlPolicy.requirePublicHttpsStore(connection.getStoreUrl());
        try {
            return connectorFactory.require(connection.getPlatform()).listProducts(store,
                    credentialCipher.decrypt(connection.getEncryptedCredentials()));
        } catch (org.example.storeintegration.connector.TokenRefreshedException tre) {
            // Persist refreshed tokens and retry once
            var refreshed = tre.refreshed();
            StoreCredentials updated = StoreCredentials.shopify(refreshed.accessToken(), refreshed.refreshToken(),
                    refreshed.expiresIn() == null ? null
                            : Instant.now().plusSeconds(refreshed.expiresIn()).getEpochSecond());
            connection.setEncryptedCredentials(credentialCipher.encrypt(updated));
            connectionRepository.save(connection);
            return connectorFactory.require(connection.getPlatform()).listProducts(store, updated);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductMappingResponse> mappings(Long connectionId) {
        requireConnection(connectionId);
        return mappingRepository.findAllByConnectionIdAndEnterpriseIdOrderByExternalNameAsc(connectionId,
                TenantContext.requireEnterpriseId()).stream().map(this::mappingResponse).toList();
    }

    @Transactional
    public ProductMappingResponse mapProduct(Long connectionId, ProductMappingRequest request) {
        StoreConnection connection = requireConnection(connectionId);
        coreClient.verifyProductAndLocation(connection.getEnterpriseId(), request.internalProductId(),
                connection.getStockLocationId());
        if (mappingRepository.findByConnectionIdAndExternalVariantId(connectionId, request.externalVariantId().trim())
                .isPresent()) {
            throw new ConflictException("This external variant is already mapped.");
        }
        ProductMapping mapping = ProductMapping.builder().connection(connection)
                .enterpriseId(connection.getEnterpriseId())
                .externalProductId(request.externalProductId().trim())
                .externalVariantId(request.externalVariantId().trim())
                .externalSku(blankToNull(request.externalSku())).externalName(request.externalName().trim())
                .internalProductId(request.internalProductId()).build();
        return mappingResponse(mappingRepository.save(mapping));
    }

    @Transactional
    public void deleteMapping(Long mappingId) {
        ProductMapping mapping = mappingRepository
                .findByIdAndEnterpriseId(mappingId, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Product mapping not found."));
        mappingRepository.delete(mapping);
    }

    @Transactional
    public AutoImportResponse autoImportProducts(Long connectionId) {
        StoreConnection connection = requireConnection(connectionId);
        URI storeUrl = connection.getPlatform() == StorePlatform.SHOPIFY
                ? urlPolicy.requireShopifyStore(connection.getStoreUrl())
                : urlPolicy.requirePublicHttpsStore(connection.getStoreUrl());
        StoreCredentials credentials = credentialCipher.decrypt(connection.getEncryptedCredentials());

        List<ExternalProduct> externalProducts;
        try {
            externalProducts = connectorFactory.require(connection.getPlatform()).listProducts(storeUrl, credentials);
        } catch (org.example.storeintegration.connector.TokenRefreshedException tre) {
            var refreshed = tre.refreshed();
            StoreCredentials updated = StoreCredentials.shopify(refreshed.accessToken(), refreshed.refreshToken(),
                    refreshed.expiresIn() == null ? null
                            : Instant.now().plusSeconds(refreshed.expiresIn()).getEpochSecond());
            connection.setEncryptedCredentials(credentialCipher.encrypt(updated));
            connectionRepository.save(connection);
            externalProducts = connectorFactory.require(connection.getPlatform()).listProducts(storeUrl, updated);
        }

        registerWebhook(connection, credentials);

        int importedCount = 0;
        int skippedCount = 0;
        List<ProductMappingResponse> createdMappings = new ArrayList<>();

        for (ExternalProduct ep : externalProducts) {
            boolean exists = mappingRepository.findByConnectionIdAndExternalVariantId(connection.getId(), ep.variantId()).isPresent();
            if (exists) {
                skippedCount++;
                continue;
            }

            Long internalId = coreClient.createProduct(connection.getEnterpriseId(), ep.name(), ep.sku(), 10.0);
            if (internalId != null) {
                ProductMapping mapping = ProductMapping.builder()
                        .connection(connection)
                        .enterpriseId(connection.getEnterpriseId())
                        .externalProductId(ep.productId())
                        .externalVariantId(ep.variantId())
                        .externalSku(blankToNull(ep.sku()))
                        .externalName(ep.name())
                        .internalProductId(internalId)
                        .build();
                createdMappings.add(mappingResponse(mappingRepository.save(mapping)));
                importedCount++;
            }
        }

        log.info("autoImportProducts: completed for store={}, imported={}, skipped={}", connection.getStoreUrl(), importedCount, skippedCount);
        return new AutoImportResponse(importedCount, skippedCount, createdMappings);
    }

    @Transactional
    public ConnectionResponse disconnect(Long connectionId) {
        StoreConnection connection = requireConnection(connectionId);
        connection.setEncryptedCredentials(
                credentialCipher.encrypt(new StoreCredentials(null, null, null, null, null, null)));
        connection.setStatus(ConnectionStatus.DISCONNECTED);
        connection.setWebhooksActive(false);
        connection.setLastError(
                "Disconnected by workspace administrator. Provider-side app access should also be revoked.");
        return response(connectionRepository.save(connection));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> recentEvents() {
        return eventRepository
                .findTop50ByConnectionEnterpriseIdOrderByReceivedAtDesc(TenantContext.requireEnterpriseId()).stream()
                .map(event -> new EventResponse(event.getId(), event.getConnection().getId(),
                        event.getExternalEventId(), event.getTopic(), event.getStatus(), event.getErrorMessage(),
                        event.getReceivedAt(), event.getProcessedAt()))
                .toList();
    }

    private StoreConnection saveConnection(OAuthState state, StoreCredentials credentials) {
        StoreConnection connection = connectionRepository.findByEnterpriseIdAndPlatformAndStoreUrl(
                state.getEnterpriseId(), state.getPlatform(), state.getStoreUrl()).orElseGet(StoreConnection::new);
        if (connection.getId() != null && connection.getStatus() != ConnectionStatus.DISCONNECTED)
            throw new ConflictException("This store is already connected.");
        connection.setEnterpriseId(state.getEnterpriseId());
        connection.setPlatform(state.getPlatform());
        connection.setDisplayName(state.getDisplayName());
        connection.setStoreUrl(state.getStoreUrl());
        connection.setStockLocationId(state.getStockLocationId());
        connection.setEncryptedCredentials(credentialCipher.encrypt(credentials));
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setWebhooksActive(false);
        connection.setLastError(null);
        return connectionRepository.save(connection);
    }

    private void registerWebhook(StoreConnection connection, StoreCredentials credentials) {
        try {
            URI store = connection.getPlatform() == StorePlatform.SHOPIFY
                    ? urlPolicy.requireShopifyStore(connection.getStoreUrl())
                    : urlPolicy.requirePublicHttpsStore(connection.getStoreUrl());
            connectorFactory.require(connection.getPlatform()).registerOrderWebhook(store, credentials,
                    publicUrl("/api/v1/integrations/webhooks/" + connection.getPlatform().name().toLowerCase() + "/"
                            + connection.getId()));
            connection.setWebhooksActive(true);
            connection.setStatus(ConnectionStatus.CONNECTED);
            connection.setLastError(null);
        } catch (RuntimeException exception) {
            // If token was refreshed during webhook registration, persist and retry once
            if (exception instanceof org.example.storeintegration.connector.TokenRefreshedException tre) {
                var refreshed = tre.refreshed();
                StoreCredentials updated = StoreCredentials.shopify(refreshed.accessToken(), refreshed.refreshToken(),
                        refreshed.expiresIn() == null ? null
                                : Instant.now().plusSeconds(refreshed.expiresIn()).getEpochSecond());
                connection.setEncryptedCredentials(credentialCipher.encrypt(updated));
                connectionRepository.save(connection);
                try {
                    URI store = connection.getPlatform() == StorePlatform.SHOPIFY
                            ? urlPolicy.requireShopifyStore(connection.getStoreUrl())
                            : urlPolicy.requirePublicHttpsStore(connection.getStoreUrl());
                    connectorFactory.require(connection.getPlatform()).registerOrderWebhook(store, updated,
                            publicUrl("/api/v1/integrations/webhooks/" + connection.getPlatform().name().toLowerCase()
                                    + "/" + connection.getId()));
                    connection.setWebhooksActive(true);
                    connection.setStatus(ConnectionStatus.CONNECTED);
                    connection.setLastError(null);
                    connectionRepository.save(connection);
                    return;
                } catch (RuntimeException inner) {
                    connection.setWebhooksActive(false);
                    connection.setStatus(ConnectionStatus.CONNECTED);
                    connection.setLastError(
                            "Store connected, but order webhook registration pending: " + safeMessage(inner));
                    connectionRepository.save(connection);
                    return;
                }
            }
            connection.setWebhooksActive(false);
            connection.setStatus(ConnectionStatus.CONNECTED);
            connection
                    .setLastError("Store connected, but order webhook registration pending: " + safeMessage(exception));
        }
        connectionRepository.save(connection);
    }

    private StateToken createState(StorePlatform platform, ConnectRequest request, URI store) {
        String raw = randomToken();
        Instant expiry = Instant.now().plus(Duration.ofMinutes(10));
        stateRepository.save(OAuthState.builder().stateHash(hash(raw)).enterpriseId(TenantContext.requireEnterpriseId())
                .userId(TenantContext.requireUserId()).platform(platform).displayName(request.displayName().trim())
                .storeUrl(store.toString()).stockLocationId(request.stockLocationId()).expiresAt(expiry).build());
        return new StateToken(raw, expiry);
    }

    private OAuthState requireState(String raw, StorePlatform platform) {
        OAuthState state = stateRepository.findByStateHash(hash(raw))
                .orElseThrow(() -> new SecurityException("Unknown authorization state."));
        if (state.getPlatform() != platform || state.getConsumedAt() != null
                || state.getExpiresAt().isBefore(Instant.now()))
            throw new SecurityException("Authorization state is expired or already used.");
        return state;
    }

    private StoreConnection requireConnection(Long id) {
        return connectionRepository.findByIdAndEnterpriseId(id, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Store connection not found."));
    }

    private void verifyLocation(Long enterpriseId, Long locationId) {
        coreClient.verifyLocation(enterpriseId, locationId);
    }

    private String publicUrl(String path) {
        requirePublicCallbacks();
        return properties.publicBaseUrl().replaceAll("/$", "") + path;
    }

    private void requirePublicCallbacks() {
        if (!properties.hasPublicCallbackUrl())
            throw new IllegalStateException("An HTTPS integration.public-base-url is required for provider callbacks.");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (provided == null)
            return false;
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(message);
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        return value == null ? exception.getClass().getSimpleName() : value.substring(0, Math.min(700, value.length()));
    }

    private ConnectionResponse response(StoreConnection c) {
        return new ConnectionResponse(c.getId(), c.getPlatform(), c.getDisplayName(), c.getStoreUrl(),
                c.getStockLocationId(), c.getStatus(), c.isWebhooksActive(), c.getLastError(), c.getLastSyncAt(),
                c.getCreatedAt());
    }

    private ProductMappingResponse mappingResponse(ProductMapping m) {
        return new ProductMappingResponse(m.getId(), m.getConnection().getId(), m.getExternalProductId(),
                m.getExternalVariantId(), m.getExternalSku(), m.getExternalName(), m.getInternalProductId(),
                m.getCreatedAt());
    }

    public record StartedAuthorization(AuthorizationResponse response, String browserState) {
    }

    private record StateToken(String raw, Instant expiresAt) {
    }
}
