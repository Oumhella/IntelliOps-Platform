package org.example.stock_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "boutiques")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Boutique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idBoutique;

    @Column(nullable = false)
    private String nomBoutique;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypePlateforme plateformeType;

    private String cleApi;
    private Long adminId;

    @OneToMany(mappedBy = "boutique", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<Inventaire> inventaires = new ArrayList<>();

    // Méthodes Métier d'encapsulation
    public boolean connecterPlateforme(String cleApi) {
        this.cleApi = cleApi;
        return testerConnexionAPI();
    }

    public boolean testerConnexionAPI() {
        return this.cleApi != null && !this.cleApi.isBlank();
    }
}