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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'MANUAL'")
    @Builder.Default
    private LeadSource source = LeadSource.MANUAL;

    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;
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
        if (!isAllowedTransition(this.statutLead, nouveauStatut)) {
            throw new IllegalStateException(
                    "Invalid lead transition from " + this.statutLead + " to " + nouveauStatut + ".");
        }
        this.statutLead = nouveauStatut;
    }

    public Commande convertirEnCommande(Long stockLocationId, String orderReference) {
        if (this.statutLead != StatutLead.IN_PROGRESS) {
            throw new IllegalStateException("Only a qualified lead in progress can be converted to an order.");
        }
        if (this.statutLead == StatutLead.CONVERTED) {
            throw new IllegalStateException("Ce lead a déjà été converti en commande.");
        }
        this.statutLead = StatutLead.CONVERTED;

        // Initialisation de la commande liée
        this.commande = Commande.builder()
                .lead(this)
                .reference(orderReference)
                .statutCommande(StatutCommande.EN_ATTENTE)
                .infosClient(this.infosClient)
                .stockLocationId(stockLocationId)
                .stockReservationReference(orderReference)
                .totalPrix(0.0)
                .build();

        return this.commande;
    }

    private boolean isAllowedTransition(StatutLead current, StatutLead next) {
        if (current == null || next == null || current == next || next == StatutLead.CONVERTED) {
            return false;
        }
        return switch (current) {
            case NEW_LEAD -> next == StatutLead.ATTEMPTED_CONTACT
                    || next == StatutLead.IN_PROGRESS
                    || next == StatutLead.SCHEDULED_RECALL
                    || next == StatutLead.UNREACHABLE
                    || next == StatutLead.REFUSED;
            case ATTEMPTED_CONTACT -> next == StatutLead.IN_PROGRESS
                    || next == StatutLead.SCHEDULED_RECALL
                    || next == StatutLead.UNREACHABLE
                    || next == StatutLead.REFUSED;
            case IN_PROGRESS -> next == StatutLead.SCHEDULED_RECALL
                    || next == StatutLead.UNREACHABLE
                    || next == StatutLead.REFUSED;
            case SCHEDULED_RECALL -> next == StatutLead.ATTEMPTED_CONTACT
                    || next == StatutLead.IN_PROGRESS
                    || next == StatutLead.UNREACHABLE
                    || next == StatutLead.REFUSED;
            case UNREACHABLE -> next == StatutLead.SCHEDULED_RECALL
                    || next == StatutLead.ATTEMPTED_CONTACT
                    || next == StatutLead.REFUSED;
            case REFUSED, CONVERTED -> false;
        };
    }
}
