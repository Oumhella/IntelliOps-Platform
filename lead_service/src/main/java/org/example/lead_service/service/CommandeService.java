package org.example.lead_service.service;

import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.entity.StatutCommande;
import org.example.common.dto.PageResponse;

public interface CommandeService {
    CommandeDTO obtenirCommandeParId(Long idCommande);
    PageResponse<CommandeDTO> rechercherCommandes(StatutCommande statut, int page, int size);
    CommandeDTO ajouterProduitACommande(Long idCommande, Long produitId, int quantite, double prixUnitaire);
    CommandeDTO changerStatutCommande(Long idCommande, StatutCommande nouveauStatut);
}
