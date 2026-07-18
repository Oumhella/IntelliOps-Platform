package org.example.lead_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.CreationCommandeRequest;
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

        // Création de la note d'interaction
        NoteInteraction note = NoteInteraction.builder()
                .lead(lead)
                .ancienStatut(ancienStatutStr)
                .nouveauStatut(lead.getStatutLead().name())
                .typeInteraction(type)
                .commentaireAgent(commentaire)
                .build();

        lead.getHistoriqueInteractions().add(note);

        // 1. Sauvegarde l'ensemble via la cascade
        Lead savedLead = leadRepository.save(lead);

        // 2. On extrait la note qui vient d'être enregistrée (la dernière de la liste)
        NoteInteraction savedNote = savedLead.getHistoriqueInteractions()
                .stream()
                .reduce((first, second) -> second) // Récupère le dernier élément de la liste
                .orElseThrow(() -> new IllegalStateException("Erreur lors de la récupération de la note enregistrée"));

        // 3. Appel de la méthode de mapping spécifique à NoteInteraction
        return leadMapper.toDto(savedNote);
    }

    @Override
    @Transactional
    public CommandeDTO convertirEnCommande(Long idLead, CreationCommandeRequest request) {
        Lead lead = leadRepository.findById(idLead)
                .orElseThrow(() -> new EntityNotFoundException("Lead introuvable avec l'ID : " + idLead));

        // 1. Initialise la commande avec le statut CONVERTED et copie les infos du client
        Commande nouvelleCommande = lead.convertirEnCommande();

        // 2. On utilise TES méthodes métier pour insérer proprement chaque ligne
        if (request.getItems() != null) {
            for (CreationCommandeRequest.ItemRequest item : request.getItems()) {
                nouvelleCommande.ajouterLigne(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice()
                );
            }
        }

        // 3. Juste au cas où le totalAmount du JSON doit surcharger le calcul (ou pour vérification)
        // nouvelleCommande.setTotalPrix(request.getTotalAmount());
        // Mais en théorie, ton nouvelleCommande.calculerTotal() appelé dans ajouterLigne() fait déjà le taf !

        // 4. On sauvegarde le Lead (qui va propager la sauvegarde à Commande grâce à cascade = CascadeType.ALL)
        Lead savedLead = leadRepository.save(lead);

        // 5. On renvoie la commande persistée mappée en DTO
        return commandeMapper.toDto(savedLead.getCommande());
    }
}