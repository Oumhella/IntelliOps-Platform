package org.example.stock_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "produits")
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

    @Column(unique = true, nullable = false)
    private String globalSku;

    // Méthode métier
    public boolean estEnRupture(int totalQuantiteDisponible) {
        return totalQuantiteDisponible <= 0;
    }
}