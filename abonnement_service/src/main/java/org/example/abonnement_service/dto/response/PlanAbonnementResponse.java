package org.example.abonnement_service.dto.response;

import lombok.*;
import org.example.abonnement_service.entity.DureeOffre;
import org.example.abonnement_service.entity.StatutOffre;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanAbonnementResponse {
    private Long idPlan;
    private String nomPlan;
    private String description;
    private Double prix;
    private DureeOffre duree;
    private StatutOffre estActif;
    private int limiteCommandesMois;
}