package org.example.abonnement_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "desactivations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Desactivation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate dateDebutDesactivation;

    @Column(nullable = false)
    private LocalDate dateFinDesactivation;

    @ManyToOne(optional = false)
    @JoinColumn(name = "abonnement_id", nullable = false)
    private Abonnement abonnement;
}
