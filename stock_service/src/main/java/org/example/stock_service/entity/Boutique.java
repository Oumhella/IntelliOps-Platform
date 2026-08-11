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

    private Long adminId;

    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    @OneToMany(mappedBy = "boutique", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<Inventaire> inventaires = new ArrayList<>();

    // Méthodes Métier d'encapsulation
}
