package org.example.lead_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notes_interaction")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHistorique;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    @ToString.Exclude
    private Lead lead;

    private String ancienStatut;
    private String nouveauStatut;

    @Enumerated(EnumType.STRING)
    private TypeInteraction typeInteraction;

    private LocalDateTime dateChangement;

    @Column(columnDefinition = "TEXT")
    private String commentaireAgent;

    @PrePersist
    protected void onCreate() {
        this.dateChangement = LocalDateTime.now();
    }
}