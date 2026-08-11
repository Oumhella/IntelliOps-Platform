package org.example.stock_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReservationStockRequest(
        @Positive int quantite,
        @NotBlank @Size(max = 100) String referenceOperation
) {
}
