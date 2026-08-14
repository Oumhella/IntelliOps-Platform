package org.example.lead_service.service;

import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.entity.StatutCommande;
import org.example.common.dto.PageResponse;
import org.example.lead_service.dto.AddOrderLineRequest;
import org.example.lead_service.entity.StatutPaiementCommande;

public interface CommandeService {
    CommandeDTO obtenirCommandeParId(Long idCommande);
    PageResponse<CommandeDTO> rechercherCommandes(StatutCommande statut, Long agentId, int page, int size);
    CommandeDTO ajouterProduitACommande(Long idCommande, AddOrderLineRequest request);
    CommandeDTO changerStatutCommande(Long idCommande, StatutCommande nouveauStatut);
    CommandeDTO changerStatutPaiement(Long idCommande, StatutPaiementCommande nouveauStatut);
}
