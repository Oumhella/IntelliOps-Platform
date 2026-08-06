package org.example.paiment_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transactions_paiement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_tenant_idempotency", columnNames = {"enterprise_id", "idempotency_key"}),
                @UniqueConstraint(name = "uk_payment_provider_transaction", columnNames = {"provider_transaction_id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPaiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    @Column(nullable = false)
    private Long referenceSourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Contexte typeContexte;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montantRembourse = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModePaiement mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPaiement statut;

    @Column(name = "provider_transaction_id", length = 120)
    private String providerTransactionId;

    @Column(name = "consumption_reference", length = 180)
    private String consumptionReference;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "facture_id", referencedColumnName = "id")
    private Facture facture;

    // Retained only for backward-compatible reads of existing development data.
    // New payments never persist a card token or PaymentMethod identifier here.
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "tokenisation_id", referencedColumnName = "id")
    private ModeleTokenisation tokenisation;

    public void markConsumed(String reference) {
        if (consumptionReference != null && !consumptionReference.equals(reference)) {
            throw new IllegalStateException("Payment has already been used by another business operation.");
        }
        if (consumptionReference == null) {
            consumptionReference = reference;
            consumedAt = LocalDateTime.now();
        }
    }

    public void annuler() {
        if (statut == StatutPaiement.COMPLETED || statut == StatutPaiement.PARTIALLY_REFUNDED
                || statut == StatutPaiement.REFUNDED) {
            throw new IllegalStateException("A captured payment must be refunded instead of cancelled.");
        }
        statut = StatutPaiement.CANCELLED;
    }

    public void rembourser(BigDecimal montantARembourser) {
        if (statut != StatutPaiement.COMPLETED && statut != StatutPaiement.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Only a completed payment can be refunded.");
        }
        BigDecimal newTotal = montantRembourse.add(montantARembourser);
        if (newTotal.compareTo(montant) > 0) {
            throw new IllegalArgumentException("Total refunds cannot exceed the captured amount.");
        }
        montantRembourse = newTotal;
        statut = newTotal.compareTo(montant) == 0
                ? StatutPaiement.REFUNDED
                : StatutPaiement.PARTIALLY_REFUNDED;
    }
}
