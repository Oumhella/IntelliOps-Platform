package org.example.paiment_service.dto.response;

import lombok.Data;
import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.ModePaiement;
import org.example.paiment_service.entity.StatutPaiement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionPaiementResponseDTO {
    private Long id;
    private String idempotencyKey;
    private Long referenceSourceId;
    private Contexte typeContexte;
    private BigDecimal montant;
    private BigDecimal montantRembourse;
    private ModePaiement mode;
    private StatutPaiement statut;
    private String providerTransactionId;
    private String consumptionReference;
    private LocalDateTime consumedAt;
    private FactureResponseDTO facture;
}
