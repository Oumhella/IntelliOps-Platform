package org.example.stock_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inventaires")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantiteDisponible;
    private int quantiteReservee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boutique_id", nullable = false)
    @ToString.Exclude
    private Boutique boutique;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id", nullable = false)
    @ToString.Exclude
    private Produit produit;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "regle_approvisionnement_id", referencedColumnName = "id")
    private RegleApprovisionnement regleApprovisionnement;

    @OneToMany(mappedBy = "inventaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<MouvementStock> mouvements = new ArrayList<>();

    // --- Encapsulation Métier avec Audit Journalisé ---

    public void updateQuantity(int deltaQty, TypeMouvement type, Long auteurId) {
        this.quantiteDisponible += deltaQty;
        if (this.quantiteDisponible < 0) {
            throw new IllegalArgumentException("Le stock disponible ne peut pas être négatif.");
        }

        // Entrée immuable dans l'historique
        MouvementStock mouvement = MouvementStock.builder()
                .inventaire(this)
                .quantite(deltaQty)
                .typeMouvement(type)
                .auteurId(auteurId)
                .build();
        this.mouvements.add(mouvement);
    }

    public void reserveStock(int qty, Long auteurId) {
        if (qty <= 0) {
            throw new IllegalArgumentException("La quantite reservee doit etre strictement positive.");
        }
        if (this.quantiteDisponible < qty) {
            throw new IllegalStateException("Stock disponible insuffisant pour réserver " + qty + " unités.");
        }
        this.quantiteDisponible -= qty;
        this.quantiteReservee += qty;

        MouvementStock mouvement = MouvementStock.builder()
                .inventaire(this)
                .quantite(qty)
                .typeMouvement(TypeMouvement.RESERVATION)
                .auteurId(auteurId)
                .build();
        this.mouvements.add(mouvement);
    }

    public void releaseReservation(int qty, Long auteurId) {
        if (qty <= 0 || this.quantiteReservee < qty) {
            throw new IllegalStateException("La quantite reservee ne permet pas cette liberation.");
        }
        this.quantiteReservee -= qty;
        this.quantiteDisponible += qty;
        this.mouvements.add(MouvementStock.builder()
                .inventaire(this)
                .quantite(qty)
                .typeMouvement(TypeMouvement.LIBERATION)
                .auteurId(auteurId)
                .build());
    }

    public void consumeReservation(int qty, Long auteurId) {
        if (qty <= 0 || this.quantiteReservee < qty) {
            throw new IllegalStateException("La quantite reservee ne permet pas cette consommation.");
        }
        this.quantiteReservee -= qty;
        this.mouvements.add(MouvementStock.builder()
                .inventaire(this)
                .quantite(-qty)
                .typeMouvement(TypeMouvement.VENTE)
                .auteurId(auteurId)
                .build());
    }
}
