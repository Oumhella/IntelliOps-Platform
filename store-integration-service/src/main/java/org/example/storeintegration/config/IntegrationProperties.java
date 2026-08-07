package org.example.storeintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration")
public record IntegrationProperties(
        String publicBaseUrl,
        String frontendReturnUrl,
        boolean allowPrivateStoreHosts,
        long serviceTokenTtlSeconds,
        String orderCurrency,
        Shopify shopify,
        String credentialsMasterKey
) {
    public record Shopify(String clientId, String clientSecret, String scopes, String apiVersion) {}

    public boolean hasPublicCallbackUrl() {
        return publicBaseUrl != null && publicBaseUrl.startsWith("https://");
    }

    public boolean shopifyConfigured() {
        return hasPublicCallbackUrl() && shopify != null
                && shopify.clientId() != null && !shopify.clientId().isBlank()
                && shopify.clientSecret() != null && !shopify.clientSecret().isBlank();
    }
}
