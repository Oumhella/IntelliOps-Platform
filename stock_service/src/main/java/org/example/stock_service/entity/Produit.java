package org.example.stock_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "produits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"enterprise_id", "global_sku"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Produit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idProduit;

    @Column(nullable = false)
    private String nomProduit;

    private double prixAchat;
    private double prixVente;

    @Column(name = "global_sku", nullable = false)
    private String globalSku;

    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    // Méthode métier
    public boolean estEnRupture(int totalQuantiteDisponible) {
        return totalQuantiteDisponible <= 0;
    }
}
