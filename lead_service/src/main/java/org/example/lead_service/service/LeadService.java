package org.example.lead_service.service;

import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.LeadDTO;
import org.example.lead_service.dto.NoteInteractionDTO;
import org.example.lead_service.entity.StatutLead;
import org.example.lead_service.entity.TypeInteraction;

import java.util.List;

public interface LeadService {
    LeadDTO creerLead(LeadDTO leadDTO);
    LeadDTO obtenirLeadParId(Long idLead);
    List<LeadDTO> obtenirLeadsParAgent(Long agentId);

    LeadDTO assignerAgent(Long idLead, Long agentId);

    NoteInteractionDTO enregistrerInteraction(Long idLead, TypeInteraction type, String commentaire, StatutLead nouveauStatut);

    CommandeDTO convertirEnCommande(Long idLead);
}