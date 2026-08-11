package org.example.abonnement_service.dto.response;

import java.math.BigDecimal;

public record CheckoutPreparationResponse(
        Long paymentId,
        String clientSecret,
        String publishableKey,
        BigDecimal amount,
        String currency) {
}
