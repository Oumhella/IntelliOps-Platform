package org.example.paiment_service.dto.response;

import lombok.Data;
import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.ModePaiement;
import org.example.paiment_service.entity.StatutPaiement;

@Data
public class TransactionPaiementResponseDTO {
    private Long id;
    private Long referenceSourceId;
    private Contexte typeContexte;
    private double montant;
    private ModePaiement mode;
    private StatutPaiement statut;
    private FactureResponseDTO facture;
}