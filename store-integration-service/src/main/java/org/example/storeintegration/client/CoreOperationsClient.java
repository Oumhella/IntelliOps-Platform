package org.example.storeintegration.client;

import lombok.RequiredArgsConstructor;
import org.example.storeintegration.dto.IntegrationDtos.ExternalOrderRequest;
import org.example.storeintegration.dto.IntegrationDtos.ExternalOrderStateRequest;
import org.example.storeintegration.security.ServiceTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CoreOperationsClient {
    private final RestClient.Builder restClientBuilder;
    private final ServiceTokenProvider tokenProvider;
    @Value("${services.lead.url}") private String leadUrl;
    @Value("${services.stock.url}") private String stockUrl;

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
        restClientBuilder.clone().baseUrl(leadUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.forTenant(enterpriseId))
                .build().post().uri("/api/v1/internal/integrations/orders").body(request).retrieve().toBodilessEntity();
    }

    public void syncOrderState(Long enterpriseId, ExternalOrderStateRequest request) {
        restClientBuilder.clone().baseUrl(leadUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.forTenant(enterpriseId))
                .build().patch().uri("/api/v1/internal/integrations/orders/state").body(request).retrieve().toBodilessEntity();
    }

    public Long createProduct(Long enterpriseId, String name, String sku, double price) {
        String token = tokenProvider.forTenant(enterpriseId);
        RestClient stock = restClientBuilder.clone().baseUrl(stockUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
        String safeName = (name == null || name.isBlank()) ? "Unnamed Product" : (name.length() > 180 ? name.substring(0, 180) : name);
        String safeSku = (sku != null && !sku.isBlank()) ? (sku.length() > 100 ? sku.substring(0, 100) : sku) : "SKU-" + System.currentTimeMillis();
        var body = java.util.Map.of(
                "nomProduit", safeName,
                "prixAchat", 0.0,
                "prixVente", price > 0 ? price : 10.0,
                "globalSku", safeSku
        );
        com.fasterxml.jackson.databind.JsonNode response = stock.post().uri("/api/v1/produits").body(body).retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
        return response != null && response.has("idProduit") ? response.path("idProduit").asLong() : null;
    }
}
