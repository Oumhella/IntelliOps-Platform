package org.example.lead_service.dto;

import lombok.Data;
import org.example.lead_service.entity.TypeInteraction;

import java.time.LocalDateTime;

@Data
public class NoteInteractionDTO {
    private Long idHistorique;
    private String ancienStatut;
    private String nouveauStatut;
    private TypeInteraction typeInteraction;
    private LocalDateTime dateChangement;
    private String commentaireAgent;
}