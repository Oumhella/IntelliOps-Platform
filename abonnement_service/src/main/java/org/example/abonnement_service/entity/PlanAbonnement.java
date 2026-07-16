package org.example.abonnement_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plans_abonnement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanAbonnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPlan;

    @Column(nullable = false)
    private String nomPlan;

    private String description;

    @Column(nullable = false)
    private Double prix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DureeOffre duree;

    private int minJoursEntreDesactivation;
    private int maxPeriodeDesactivation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutOffre estActif;

    private int limiteCommandesMois;
}