package org.example.delivery_service.dto.response;

import lombok.Builder;
import lombok.Data;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.example.delivery_service.entity.MotifEchecLivraison;

@Data
@Builder
public class LivraisonResponse {
    private Long idLivraison;
    private Long referenceCommandeId;
    private String codeSuiviTracking;
    private StatutLivraison statutLivraison;
    private TypeTransporteur typeTransporteur;
    private String nomSociete;
    private Long livreurId;
    private LocalDateTime shippingDate;
    private LocalDateTime deliveryDate;
    private double montantACollecterCoD;
    private long delaiJours;
    private String clientNomComplet;
    private String clientEmail;
    private String clientTelephone;
    private String adresseLivraison;
    private String villeLivraison;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime returnRequestedAt;
    private int attemptCount;
    private MotifEchecLivraison failureReason;
    private String failureNote;
    private Double lastLatitude;
    private Double lastLongitude;
    private String deliveredTo;
    private String proofSignature;
    private boolean proofPhotoAvailable;
    private LocalDateTime proofCapturedAt;
    private BigDecimal codCollectedAmount;
    private String codDiscrepancyNote;
    private LocalDateTime codReconciledAt;
}
