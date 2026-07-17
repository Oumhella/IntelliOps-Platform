package org.example.lead_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.entity.Commande;
import org.example.lead_service.entity.StatutCommande;
import org.example.lead_service.mapper.CommandeMapper;
import org.example.lead_service.repository.CommandeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommandeServiceImpl implements CommandeService {

    private final CommandeRepository commandeRepository;
    private final CommandeMapper commandeMapper;

    @Override
    @Transactional(readOnly = true)
    public CommandeDTO obtenirCommandeParId(Long idCommande) {
        Commande commande = commandeRepository.findById(idCommande)
                .orElseThrow(() -> new EntityNotFoundException("Commande introuvable avec l'ID : " + idCommande));
        return commandeMapper.toDto(commande);
    }

    @Override
    public CommandeDTO ajouterProduitACommande(Long idCommande, Long produitId, int quantite, double prixUnitaire) {
        Commande commande = commandeRepository.findById(idCommande)
                .orElseThrow(() -> new EntityNotFoundException("Commande introuvable avec l'ID : " + idCommande));

        // Utilise la méthode métier de l'entité qui recalcule automatiquement le total
        commande.ajouterLigne(produitId, quantite, prixUnitaire);

        return commandeMapper.toDto(commandeRepository.save(commande));
    }

    @Override
    public CommandeDTO changerStatutCommande(Long idCommande, StatutCommande nouveauStatut) {
        Commande commande = commandeRepository.findById(idCommande)
                .orElseThrow(() -> new EntityNotFoundException("Commande introuvable avec l'ID : " + idCommande));

        commande.changerStatut(nouveauStatut);
        return commandeMapper.toDto(commandeRepository.save(commande));
    }
}