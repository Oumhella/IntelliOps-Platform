package org.example.lead_service.dto;

import lombok.Data;
import org.example.lead_service.entity.StatutLead;
import org.example.lead_service.entity.TypeInteraction;

@Data
public class NoteInteractionRequest {
    private TypeInteraction typeInteraction;
    private StatutLead nouveauStatut;
    private String commentaireAgent;
}