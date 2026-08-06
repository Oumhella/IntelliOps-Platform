package org.example.paiment_service.gateway;

import org.example.paiment_service.entity.ModePaiement;
import org.example.paiment_service.entity.StatutPaiement;

import java.math.BigDecimal;

public interface PaymentGatewayProvider {
    ModePaiement getSupportedMode();

    PreparedPayment preparerPaiement(BigDecimal montant, String idempotencyKey);

    PreparedPayment recupererPaiementPrepare(String providerTransactionId);

    PaymentResult verifierPaiement(String providerTransactionId);

    boolean traiterRemboursement(String providerTransactionId, BigDecimal montant, String idempotencyKey);

    record PreparedPayment(
            String providerTransactionId,
            String clientSecret,
            StatutPaiement status,
            BigDecimal amount,
            String currency) {
    }

    record PaymentResult(
            String providerTransactionId,
            StatutPaiement status,
            BigDecimal amount,
            String currency) {
    }
}
