package org.example.storeintegration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.storeintegration.domain.ConnectionStatus;
import org.example.storeintegration.domain.StorePlatform;
import org.example.storeintegration.domain.WebhookEventStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class IntegrationDtos {
    private IntegrationDtos() {}

    public record ConnectRequest(@NotBlank String displayName, @NotBlank String store, @NotNull @Positive Long stockLocationId) {}
    public record AuthorizationResponse(String authorizationUrl, Instant expiresAt) {}
    public record CapabilitiesResponse(boolean shopify, boolean woocommerce, boolean publicCallbacks,
                                       String inventoryAuthority, String message) {}
    public record ConnectionResponse(Long id, StorePlatform platform, String displayName, String storeUrl,
                                     Long stockLocationId, ConnectionStatus status, boolean webhooksActive,
                                     String lastError, Instant lastSyncAt, Instant createdAt) {}
    public record ProductMappingRequest(@NotBlank String externalProductId, @NotBlank String externalVariantId,
                                        String externalSku, @NotBlank String externalName,
                                        @NotNull @Positive Long internalProductId) {}
    public record ProductMappingResponse(Long id, Long connectionId, String externalProductId,
                                         String externalVariantId, String externalSku, String externalName,
                                         Long internalProductId, Instant createdAt) {}
    public record ExternalProduct(String productId, String variantId, String sku, String name,
                                  BigDecimal salePrice, Integer availableQuantity) {}
    public record ExternalProductImportRequest(String name, String sku, BigDecimal salePrice,
                                               Long stockLocationId, int initialAvailableQuantity) {}
    public record ExternalProductImportResponse(Long productId, boolean productCreated,
                                                boolean inventoryCreated, int availableQuantity) {}
    public record AutoImportResponse(int importedCount, int skippedCount, List<ProductMappingResponse> mappings) {}
    public record EventResponse(Long id, Long connectionId, String externalEventId, String topic,
                                WebhookEventStatus status, String errorMessage, Instant receivedAt, Instant processedAt) {}
    public record WooAuthorizationCallback(
            @JsonProperty("key_id") Long keyId,
            @JsonProperty("user_id") String state,
            @JsonProperty("consumer_key") String consumerKey,
            @JsonProperty("consumer_secret") String consumerSecret,
            @JsonProperty("key_permissions") String keyPermissions) {}

    public record ExternalOrderRequest(String platform, String externalOrderId, String externalReference,
                                       Long stockLocationId, Customer customer, String paymentStatus,
                                       String currency, BigDecimal totalAmount,
                                       List<ExternalOrderLine> items) {}
    public record Customer(String fullName, String email, String phone, String address, String city) {}
    public record ExternalOrderLine(Long productId, int quantity, BigDecimal unitPrice) {}
    public record ExternalOrderStateRequest(String platform, String externalOrderId, String paymentStatus,
                                            boolean cancelled, BigDecimal totalAmount,
                                            List<ExternalOrderLine> items) {}
}
