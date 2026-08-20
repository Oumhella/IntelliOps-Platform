package org.example.storeintegration.client;

import lombok.RequiredArgsConstructor;
import org.example.storeintegration.dto.IntegrationDtos.ExternalOrderRequest;
import org.example.storeintegration.dto.IntegrationDtos.ExternalOrderStateRequest;
import org.example.storeintegration.dto.IntegrationDtos.ExternalProductImportRequest;
import org.example.storeintegration.dto.IntegrationDtos.ExternalProductImportResponse;
import org.example.storeintegration.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class CoreOperationsClient {
    private final RestClient.Builder restClientBuilder;
    private final ServiceTokenProvider tokenProvider;
    @Value("${services.lead.url}") private String leadUrl;
    @Value("${services.stock.url}") private String stockUrl;
    @Value("${services.http.max-attempts:3}") private int maxAttempts;
    @Value("${services.http.retry-backoff:500ms}") private Duration retryBackoff;

    public void verifyProductAndLocation(Long enterpriseId, Long productId, Long locationId) {
        String token = tokenProvider.forTenant(enterpriseId);
        RestClient stock = restClientBuilder.clone().baseUrl(stockUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
        stock.get().uri("/api/v1/boutiques/{id}", locationId).retrieve().toBodilessEntity();
        stock.get().uri("/api/v1/produits/{id}/catalog", productId).retrieve().toBodilessEntity();
    }

    public void verifyLocation(Long enterpriseId, Long locationId) {
        restClientBuilder.clone().baseUrl(stockUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.forTenant(enterpriseId))
                .build().get().uri("/api/v1/boutiques/{id}", locationId).retrieve().toBodilessEntity();
    }

    public void importOrder(Long enterpriseId, ExternalOrderRequest request) {
        executeWithRetry(() -> restClientBuilder.clone().baseUrl(leadUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.forTenant(enterpriseId))
                .build().post().uri("/api/v1/internal/integrations/orders").body(request).retrieve()
                .toBodilessEntity());
    }

    public void syncOrderState(Long enterpriseId, ExternalOrderStateRequest request) {
        executeWithRetry(() -> restClientBuilder.clone().baseUrl(leadUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.forTenant(enterpriseId))
                .build().patch().uri("/api/v1/internal/integrations/orders/state").body(request).retrieve()
                .toBodilessEntity());
    }

    public Long importProduct(Long enterpriseId, String name, String sku, BigDecimal price,
            Long stockLocationId, int initialAvailableQuantity) {
        String safeName = (name == null || name.isBlank()) ? "Unnamed Product" : (name.length() > 180 ? name.substring(0, 180) : name);
        String safeSku = (sku != null && !sku.isBlank())
                ? (sku.length() > 100 ? sku.substring(0, 100) : sku)
                : "EXT-" + Integer.toUnsignedString(safeName.hashCode());
        var body = new ExternalProductImportRequest(safeName, safeSku, price, stockLocationId,
                Math.max(0, initialAvailableQuantity));
        ExternalProductImportResponse response = executeWithRetry(() -> restClientBuilder.clone().baseUrl(stockUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.forTenant(enterpriseId)).build()
                .post().uri("/api/v1/internal/integrations/catalog/products").body(body).retrieve()
                .body(ExternalProductImportResponse.class));
        return response == null ? null : response.productId();
    }

    private <T> T executeWithRetry(Supplier<T> operation) {
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return operation.get();
            } catch (RestClientException exception) {
                if (!isTransient(exception) || attempt == attempts) {
                    throw exception;
                }
                pauseBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("Internal service call exhausted without a result.");
    }

    private boolean isTransient(RestClientException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof RestClientResponseException response) {
            HttpStatusCode status = response.getStatusCode();
            return status.is5xxServerError() || status.value() == 429;
        }
        return false;
    }

    private void pauseBeforeRetry(int attempt) {
        long baseDelay = retryBackoff == null ? 500L : Math.max(0L, retryBackoff.toMillis());
        try {
            Thread.sleep(baseDelay * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying an internal service call.", interrupted);
        }
    }
}
