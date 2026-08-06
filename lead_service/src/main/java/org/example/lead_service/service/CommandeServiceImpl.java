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

        if (commande.getStatutCommande() != StatutCommande.EN_ATTENTE) {
            throw new IllegalStateException("Order lines can only be edited while the order is pending.");
        }
        if (!TenantContext.requireUserId().equals(commande.getLead().getAgentId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the CSM assigned to the originating lead can edit this order.");
        }
        if (quantite <= 0 || prixUnitaire < 0) {
            throw new IllegalArgumentException("Quantity must be positive and unit price cannot be negative.");
        }

        // Utilise la méthode métier de l'entité qui recalcule automatiquement le total
        commande.ajouterLigne(produitId, quantite, prixUnitaire);

        return commandeMapper.toDto(commandeRepository.save(commande));
    }

    @Override
    public CommandeDTO changerStatutCommande(Long idCommande, StatutCommande nouveauStatut) {
        Commande commande = findOrder(idCommande);

        if (!isAllowedTransition(commande.getStatutCommande(), nouveauStatut)) {
            throw new IllegalStateException(
                    "Invalid order transition from " + commande.getStatutCommande() + " to " + nouveauStatut + ".");
        }

        commande.changerStatut(nouveauStatut);
        return commandeMapper.toDto(commandeRepository.save(commande));
    }

    private Commande findOrder(Long idCommande) {
        return commandeRepository.findByIdCommandeAndLeadEnterpriseId(
                        idCommande, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Commande introuvable avec l'ID : " + idCommande));
    }

    private boolean isAllowedTransition(StatutCommande current, StatutCommande next) {
        if (current == next) return false;
        return switch (current) {
            case EN_ATTENTE -> next == StatutCommande.CONFIRMEE || next == StatutCommande.ANNULEE;
            case CONFIRMEE -> next == StatutCommande.PREPARATION || next == StatutCommande.ANNULEE;
            case PREPARATION -> next == StatutCommande.EXPEDIEE || next == StatutCommande.ANNULEE;
            case EXPEDIEE -> next == StatutCommande.LIVREE || next == StatutCommande.RETOURNEE;
            case LIVREE -> next == StatutCommande.RETOURNEE;
            case ANNULEE, RETOURNEE -> false;
        };
    }
}
