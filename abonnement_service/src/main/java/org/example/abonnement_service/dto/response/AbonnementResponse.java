package org.example.abonnement_service.dto.response;

import lombok.*;
import org.example.abonnement_service.entity.StatutAbonnement;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbonnementResponse {
    private Long idAbonnement;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private StatutAbonnement statut;
    private Double prixPaye;
    private Long userId;
    private Long paiementId;
    private PlanAbonnementResponse planAbonnement;
}