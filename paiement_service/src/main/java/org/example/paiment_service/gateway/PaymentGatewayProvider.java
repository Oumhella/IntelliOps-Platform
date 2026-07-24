package org.example.paiment_service.gateway;

import org.example.paiment_service.entity.ModePaiement;

public interface PaymentGatewayProvider {
    ModePaiement getSupportedMode();
    boolean traiterPaiement(double montant, String tokenCarte);
    boolean traiterRemboursement(String transactionToken, double montant);
}