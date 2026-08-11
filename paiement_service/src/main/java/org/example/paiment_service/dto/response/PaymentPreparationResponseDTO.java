package org.example.paiment_service.dto.response;

import org.example.paiment_service.entity.StatutPaiement;

import java.math.BigDecimal;

public record PaymentPreparationResponseDTO(
        Long paymentId,
        String clientSecret,
        String publishableKey,
        BigDecimal amount,
        String currency,
        StatutPaiement status) {
}
