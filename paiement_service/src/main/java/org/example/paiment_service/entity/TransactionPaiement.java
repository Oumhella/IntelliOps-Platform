package org.example.paiment_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transactions_paiement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPaiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey; // Anti-double débit

    @Column(nullable = false)
    private Long referenceSourceId; // Order ID ou Subscription ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Contexte typeContexte;

    @Column(nullable = false)
    private double montant;

    private double montantRembourse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModePaiement mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPaiement statut;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "facture_id", referencedColumnName = "id")
    private Facture facture;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "tokenisation_id", referencedColumnName = "id")
    private ModeleTokenisation tokenisation;

    // --- Méthodes Métiers ---

    public void initierPaiement() {
        if (this.mode == ModePaiement.CASH_ON_DELIVERY) {
            this.statut = StatutPaiement.AWAITING_COLLECTION;
        } else if (this.mode == ModePaiement.CREDIT_CARD) {
            if (this.tokenisation != null && this.tokenisation.verifierValidite()) {
                this.statut = StatutPaiement.COMPLETED;
            } else {
                this.statut = StatutPaiement.FAILED;
            }
        }
    }

    public void annuler() {
        if (this.statut == StatutPaiement.COMPLETED) {
            throw new IllegalStateException("Impossible d'annuler une transaction déjà complétée. Utilisez le remboursement.");
        }
        this.statut = StatutPaiement.CANCELLED;
    }

    public void rembourser(double montantARembourser) {
        if (this.statut != StatutPaiement.COMPLETED && this.statut != StatutPaiement.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Seule une transaction complétée peut être remboursée.");
        }
        if (this.montantRembourse + montantARembourser > this.montant) {
            throw new IllegalArgumentException("Le montant total remboursé dépasse le montant de la transaction.");
        }

        this.montantRembourse += montantARembourser;
        if (this.montantRembourse == this.montant) {
            this.statut = StatutPaiement.REFUNDED;
        } else {
            this.statut = StatutPaiement.PARTIALLY_REFUNDED;
        }
    }
}