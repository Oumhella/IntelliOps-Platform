package org.example.abonnement_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubscriptionCheckoutRequest(
        @NotNull Long planId,
        @NotBlank String idempotencyKey) {
}
