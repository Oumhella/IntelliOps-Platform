package org.example.abonnement_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompletePaymentCheckoutRequest(@NotNull @Positive Long paymentId) {
}
