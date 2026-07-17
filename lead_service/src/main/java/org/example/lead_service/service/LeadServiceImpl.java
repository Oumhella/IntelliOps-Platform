package org.example.lead_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.LeadDTO;
import org.example.lead_service.dto.NoteInteractionDTO;
import org.example.lead_service.entity.*;
import org.example.lead_service.mapper.CommandeMapper;
import org.example.lead_service.mapper.LeadMapper;
import org.example.lead_service.repository.CommandeRepository;
import org.example.lead_service.repository.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LeadServiceImpl implements LeadService {

    private final LeadRepository leadRepository;
    private final CommandeRepository commandeRepository;
    private final LeadMapper leadMapper;
    private final CommandeMapper commandeMapper;

    @Override
    public LeadDTO creerLead(LeadDTO leadDTO) {
        Lead lead = leadMapper.toEntity(leadDTO);
        // Un nouveau lead commence toujours à l'état initial défini
        if (lead.getStatutLead() == null) {
            lead.setStatutLead(StatutLead.NEW_LEAD);
        }
        Lead sauvegarde = leadRepository.save(lead);
        return leadMapper.toDto(sauvegarde);
    }

    @Override
    @Transactional(readOnly = true)
    public LeadDTO obtenirLeadParId(Long idLead) {
        Lead lead = leadRepository.findById(idLead)
                .orElseThrow(() -> new EntityNotFoundException("Lead introuvable avec l'ID : " + idLead));
        return leadMapper.toDto(lead);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadDTO> obtenirLeadsParAgent(Long agentId) {
        return leadRepository.findByAgentId(agentId).stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public LeadDTO assignerAgent(Long idLead, Long agentId) {
        Lead lead = leadRepository.findById(idLead)
                .orElseThrow(() -> new EntityNotFoundException("Lead introuvable avec l'ID : " + idLead));

        lead.assignerAgent(agentId);
        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Override
    public NoteInteractionDTO enregistrerInteraction(Long idLead, TypeInteraction type, String commentaire, StatutLead nouveauStatut) {
        Lead lead = leadRepository.findById(idLead)
                .orElseThrow(() -> new EntityNotFoundException("Lead introuvable avec l'ID : " + idLead));

        String ancienStatutStr = lead.getStatutLead().name();

        // Mise à jour du statut du lead si un nouveau statut est fourni
        if (nouveauStatut != null) {
            lead.changerStatut(nouveauStatut);
        }

        // Création de la note d'interaction liée (comme spécifié par la méthode +enregistrer du diagramme)
        NoteInteraction note = NoteInteraction.builder()
                .lead(lead)
                .ancienStatut(ancienStatutStr)
                .nouveauStatut(lead.getStatutLead().name())
                .typeInteraction(type)
                .commentaireAgent(commentaire)
                .build();

        lead.getHistoriqueInteractions().add(note);
        leadRepository.save(lead); // Sauvegarde le lead ainsi que la note en cascade

        return leadMapper.toDto(note);
    }

    @Override
    public CommandeDTO convertirEnCommande(Long idLead) {
        Lead lead = leadRepository.findById(idLead)
                .orElseThrow(() -> new EntityNotFoundException("Lead introuvable avec l'ID : " + idLead));

        // Appel de la logique d'encapsulation métier du domaine
        Commande nouvelleCommande = lead.convertirEnCommande();

        // Persistance explicite de la commande générée
        Commande commandeSauvegardee = commandeRepository.save(nouvelleCommande);
        leadRepository.save(lead);

        return commandeMapper.toDto(commandeSauvegardee);
    }
}