package org.example.lead_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commandes")
@Data
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCommande;

    @Column(unique = true, nullable = false)
    private String reference;

    private double totalPrix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCommande statutCommande;

    @Embedded
    private CoordonneesClient infosClient;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    @ToString.Exclude
    private Lead lead;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<LignesCommande> lignesCommande = new ArrayList<>();

    // --- Méthodes Métier d'Encapsulation ---

    public void ajouterLigne(Long produitId, int quantite, double prixUnitaireApplique) {
        LignesCommande ligne = LignesCommande.builder()
                .commande(this)
                .produitId(produitId)
                .quantite(quantite)
                .prixUnitaireApplique(prixUnitaireApplique)
                .build();
        this.lignesCommande.add(ligne);
        calculerTotal();
    }

    public void calculerTotal() {
        this.totalPrix = this.lignesCommande.stream()
                .mapToDouble(LignesCommande::calculerSousTotal)
                .sum();
    }

    public void confirmerCommande() {
        this.statutCommande = StatutCommande.CONFIRMEE;
    }

    public void annulerCommande(String motif) {
        this.statutCommande = StatutCommande.ANNULEE;
        // On pourrait ajouter une note de suivi logistique ici si besoin
    }

    public void changerStatut(StatutCommande nouveauStatut) {
        this.statutCommande = nouveauStatut;
    }
}