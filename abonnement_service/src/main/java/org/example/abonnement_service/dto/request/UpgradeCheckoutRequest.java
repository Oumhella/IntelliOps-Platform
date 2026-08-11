package org.example.abonnement_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpgradeCheckoutRequest(
        @NotNull Long newPlanId,
        @NotBlank String idempotencyKey) {
}
