package org.example.abonnement_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "changements_plan", uniqueConstraints =
        @UniqueConstraint(name = "uk_plan_change_payment", columnNames = "paiement_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangementPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ancien_plan_id", nullable = false)
    private Long ancienPlanId;

    @Column(name = "nouveau_plan_id", nullable = false)
    private Long nouveauPlanId;

    @Column(name = "paiement_id", nullable = false)
    private Long paiementId;

    @Column(nullable = false)
    private Double montant;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "abonnement_id", nullable = false)
    private Abonnement abonnement;
}
