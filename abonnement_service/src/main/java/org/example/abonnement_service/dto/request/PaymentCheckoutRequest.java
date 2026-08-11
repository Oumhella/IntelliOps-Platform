package org.example.abonnement_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentCheckoutRequest(
        @NotBlank String idempotencyKey) {
}
