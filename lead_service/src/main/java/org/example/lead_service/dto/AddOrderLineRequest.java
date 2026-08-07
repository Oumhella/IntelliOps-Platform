package org.example.lead_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddOrderLineRequest(
        @NotNull Long productId,
        @Positive int quantity
) {
}
