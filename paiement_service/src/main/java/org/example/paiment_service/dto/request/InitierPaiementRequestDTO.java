package org.example.paiment_service.dto.request;

import lombok.Data;
import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.ModePaiement;

@Data
public class InitierPaiementRequestDTO {
    private String idempotencyKey; // Transmis par le client / frontend
    private Long referenceSourceId;
    private Contexte typeContexte;
    private double montant;
    private ModePaiement mode;
    private String tokenCarteSecurise; // Utilisé si CREDIT_CARD
    private Long systemAccountId;
}