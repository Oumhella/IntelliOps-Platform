package org.example.abonnement_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.example.abonnement_service.entity.DureeOffre;
import org.example.abonnement_service.entity.StatutOffre;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanAbonnementRequest {

    @NotBlank(message = "Le nom du plan est obligatoire")
    private String nomPlan;

    private String description;

    @NotNull(message = "Le prix est obligatoire")
    @Positive(message = "Le prix doit être positif")
    private Double prix;

    @NotNull(message = "La durée est obligatoire")
    private DureeOffre duree;

    private int minJoursEntreDesactivation;
    private int maxPeriodeDesactivation;

    @NotNull(message = "Le statut est obligatoire")
    private StatutOffre estActif;

    private int limiteCommandesMois;
}
