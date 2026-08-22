package org.example.stock_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ExternalProductImportRequest(
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 100) String sku,
        @NotNull @Positive BigDecimal salePrice,
        @NotNull @Positive Long stockLocationId,
        @PositiveOrZero Integer availableQuantity,
        @Positive Long internalProductId) {
}
