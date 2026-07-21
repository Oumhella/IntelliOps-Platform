package org.example.stock_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "regles_approvisionnement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegleApprovisionnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int seuilAlerte;
    private int quantiteRecommandeAuto;

    @Builder.Default
    private Boolean estActif = true;

    @OneToOne(mappedBy = "regleApprovisionnement")
    @ToString.Exclude
    private Inventaire inventaire;

    public void update(int seuilAlerte, int quantiteRecommandeAuto, boolean estActif) {
        this.seuilAlerte = seuilAlerte;
        this.quantiteRecommandeAuto = quantiteRecommandeAuto;
        this.estActif = estActif;
    }
}