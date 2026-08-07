package org.example.delivery_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "livraisons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_tenant_order",
                columnNames = {"enterprise_id", "reference_commande_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Livraison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idLivraison;

    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    @Column(name = "reference_commande_id", nullable = false)
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

    // Internal courier assignment (if LIVREUR_INTERNE). The legacy column name
    // is retained so existing development data remains readable.
    @Column(name = "external_livreur_id")
    private Long livreurId;

    private LocalDateTime shippingDate;
    private LocalDateTime deliveryDate;

    private String clientEmail;

    private String clientNomComplet;
    private String clientTelephone;
    private String adresseLivraison;
    private String villeLivraison;

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
