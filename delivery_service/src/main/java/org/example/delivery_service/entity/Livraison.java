package org.example.delivery_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "livraisons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Livraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idLivraison;

    @Column(nullable = false, unique = true)
    private Long referenceCommandeId;

    @Column(nullable = false, unique = true, length = 64)
    private String codeSuiviTracking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutLivraison statutLivraison;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeTransporteur typeTransporteur;

    // Carrier fields (if SOCIETE_LIVRAISON)
    private String nomSociete;
    private String endpointApiUrl;

    // Driver fields (if LIVREUR_INTERNE)
    private Long externalLivreurId;

    private LocalDateTime shippingDate;
    private LocalDateTime deliveryDate;

    @Column(nullable = false)
    private double montantACollecterCoD;

    public void mettreAJourStatut(StatutLivraison nouveauStatut) {
        this.statutLivraison = nouveauStatut;
        if (nouveauStatut == StatutLivraison.LIVREE) {
            this.deliveryDate = LocalDateTime.now();
        }
    }

    public long calculerDelaiJours() {
        if (shippingDate == null || deliveryDate == null) {
            return -1;
        }
        return ChronoUnit.DAYS.between(shippingDate, deliveryDate);
    }
}