package org.example.lead_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lignes_commande")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LignesCommande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idLigne;

    private int quantite;
    private double prixUnitaireApplique;
    private Long produitId; // Référence logique vers le catalogue

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    @ToString.Exclude
    private Commande commande;

    public double calculerSousTotal() {
        return this.quantite * this.prixUnitaireApplique;
    }
}