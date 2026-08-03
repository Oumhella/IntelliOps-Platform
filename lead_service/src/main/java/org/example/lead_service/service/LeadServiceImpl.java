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
import org.example.lead_service.client.StockClient;
import org.example.lead_service.dto.StockProductDTO;
import org.example.lead_service.event.OrderEventProducer;
import org.springframework.transaction.annotation.Transactional;
import org.example.common.dto.PageResponse;
import org.example.common.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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
    private final StockClient stockClient;
    private final OrderEventProducer orderEventProducer;

    @Override
    public LeadDTO creerLead(LeadDTO leadDTO) {
        if (leadDTO.getBoutiqueId() != null) {
            try {
                stockClient.obtenirBoutique(leadDTO.getBoutiqueId());
            } catch (Exception e) {
                throw new org.example.common.exception.ResourceNotFoundException("Boutique not found with ID : " + leadDTO.getBoutiqueId());
            }
        }
        Lead lead = leadMapper.toEntity(leadDTO);
        lead.setEnterpriseId(TenantContext.requireEnterpriseId());
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
        Lead lead = findLead(idLead);
        return leadMapper.toDto(lead);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadDTO> obtenirLeadsParAgent(Long agentId) {
        return leadRepository.findByAgentIdAndEnterpriseId(agentId, TenantContext.requireEnterpriseId()).stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadDTO> rechercherLeads(StatutLead statut, Long agentId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "idLead"));
        return PageResponse.from(
                leadRepository.search(TenantContext.requireEnterpriseId(), statut, agentId, pageable),
                leadMapper::toDto);
    }

    @Override
    public LeadDTO assignerAgent(Long idLead, Long agentId) {
        Lead lead = findLead(idLead);

        lead.assignerAgent(agentId);
        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Override
    public NoteInteractionDTO enregistrerInteraction(Long idLead, TypeInteraction type, String commentaire, StatutLead nouveauStatut) {
        Lead lead = findLead(idLead);

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
        Lead lead = findLead(idLead);

        // 1. Initialise la commande avec le statut CONVERTED et copie les infos du client
        Commande nouvelleCommande = lead.convertirEnCommande();

        // 2. On utilise TES méthodes métier pour insérer proprement chaque ligne après validation avec le Stock Client
        if (request.getItems() != null) {
            for (CreationCommandeRequest.ItemRequest item : request.getItems()) {
                double applyPrice = item.getUnitPrice();
                try {
                    StockProductDTO produit = stockClient.obtenirProduit(item.getProductId());
                    if (produit != null && applyPrice <= 0) {
                        applyPrice = produit.getPrixVente();
                    }
                    if (lead.getBoutiqueId() != null) {
                        stockClient.reserverStock(lead.getBoutiqueId(), item.getProductId(), item.getQuantity());
                    }
                } catch (Exception e) {
                    // Si appel feign échoue, on conserve la fallback price
                }

                nouvelleCommande.ajouterLigne(
                        item.getProductId(),
                        item.getQuantity(),
                        applyPrice
                );
            }
        }

        // 3. Juste au cas où le totalAmount du JSON doit surcharger le calcul (ou pour vérification)
        // nouvelleCommande.setTotalPrix(request.getTotalAmount());
        // Mais en théorie, ton nouvelleCommande.calculerTotal() appelé dans ajouterLigne() fait déjà le taf !

        // 4. On sauvegarde le Lead (qui va propager la sauvegarde à Commande grâce à cascade = CascadeType.ALL)
        Lead savedLead = leadRepository.save(lead);

        // Send order creation notification via Kafka
        if (savedLead.getInfosClient() != null && savedLead.getInfosClient().getEmail() != null) {
            orderEventProducer.sendOrderNotification(
                    savedLead.getInfosClient().getEmail(),
                    "Confirmation de votre commande #" + savedLead.getCommande().getIdCommande(),
                    "Votre commande #" + savedLead.getCommande().getIdCommande() + " a été créée avec succès pour un montant total de " + savedLead.getCommande().getTotalPrix() + " DH."
            );
        }

        // 5. On renvoie la commande persistée mappée en DTO
        return commandeMapper.toDto(savedLead.getCommande());
    }

    private Lead findLead(Long idLead) {
        return leadRepository.findByIdLeadAndEnterpriseId(idLead, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Lead introuvable avec l'ID : " + idLead));
    }
}
