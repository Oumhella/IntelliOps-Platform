package org.example.stock_service.dto.response;

public record ExternalProductImportResponse(
        Long productId,
        boolean productCreated,
        boolean inventoryCreated,
        int availableQuantity) {
}
