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
import org.example.common.dto.PageResponse;
import org.example.common.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
@Transactional
public class CommandeServiceImpl implements CommandeService {

    private final CommandeRepository commandeRepository;
    private final CommandeMapper commandeMapper;

    @Override
    @Transactional(readOnly = true)
    public CommandeDTO obtenirCommandeParId(Long idCommande) {
        Commande commande = findOrder(idCommande);
        return commandeMapper.toDto(commande);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommandeDTO> rechercherCommandes(StatutCommande statut, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "idCommande"));
        return PageResponse.from(
                commandeRepository.search(TenantContext.requireEnterpriseId(), statut, pageable),
                commandeMapper::toDto);
    }

    @Override
    public CommandeDTO ajouterProduitACommande(Long idCommande, Long produitId, int quantite, double prixUnitaire) {
        Commande commande = findOrder(idCommande);

        // Utilise la méthode métier de l'entité qui recalcule automatiquement le total
        commande.ajouterLigne(produitId, quantite, prixUnitaire);

        return commandeMapper.toDto(commandeRepository.save(commande));
    }

    @Override
    public CommandeDTO changerStatutCommande(Long idCommande, StatutCommande nouveauStatut) {
        Commande commande = findOrder(idCommande);

        commande.changerStatut(nouveauStatut);
        return commandeMapper.toDto(commandeRepository.save(commande));
    }

    private Commande findOrder(Long idCommande) {
        return commandeRepository.findByIdCommandeAndLeadEnterpriseId(
                        idCommande, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Commande introuvable avec l'ID : " + idCommande));
    }
}
