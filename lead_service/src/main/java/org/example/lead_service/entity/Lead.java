package org.example.lead_service.entity;


import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Builder
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "leads")
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idLead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutLead statutLead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrdrePriorite ordrePriorite;

    @Embedded
    private CoordonneesClient infosClient;

    private Long boutiqueId;
    private  Long agentId;
    @OneToOne(mappedBy = "lead", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private Commande commande;

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<NoteInteraction> historiqueInteractions = new ArrayList<>();

    // --- Méthodes Métier d'Encapsulation ---

    public void assignerAgent(Long agentId) {
        this.agentId = agentId;
    }

    public void changerStatut(StatutLead nouveauStatut) {
        this.statutLead = nouveauStatut;
    }

    public Commande convertirEnCommande() {
        if (this.statutLead == StatutLead.CONVERTED) {
            throw new IllegalStateException("Ce lead a déjà été converti en commande.");
        }
        this.statutLead = StatutLead.CONVERTED;

        // Initialisation de la commande liée
        this.commande = Commande.builder()
                .lead(this)
                .reference("CMD-" + System.currentTimeMillis())
                .statutCommande(StatutCommande.EN_ATTENTE)
                .infosClient(this.infosClient)
                .totalPrix(0.0)
                .build();

        return this.commande;
    }
}
