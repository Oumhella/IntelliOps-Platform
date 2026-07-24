package org.example.delivery_service.dto.response;

import lombok.Builder;
import lombok.Data;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;

import java.time.LocalDateTime;

@Data
@Builder
public class LivraisonResponse {
    private Long idLivraison;
    private Long referenceCommandeId;
    private String codeSuiviTracking;
    private StatutLivraison statutLivraison;
    private TypeTransporteur typeTransporteur;
    private String nomSociete;
    private Long externalLivreurId;
    private LocalDateTime shippingDate;
    private LocalDateTime deliveryDate;
    private double montantACollecterCoD;
    private long delaiJours;
}