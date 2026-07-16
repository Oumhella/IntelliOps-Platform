package org.example.abonnement_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "renouvellements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Renouvellement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paiementId;

    @Column(nullable = false)
    private LocalDate dateRenouvellement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeRenouvellement typeRenouvellement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutRenouvellement statut;

    @Column(nullable = false)
    private Double prixApplique;

    @ManyToOne(optional = false)
    @JoinColumn(name = "abonnement_id", nullable = false)
    private Abonnement abonnement;
}