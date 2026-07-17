package org.example.lead_service.service;

import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.entity.StatutCommande;

public interface CommandeService {
    CommandeDTO obtenirCommandeParId(Long idCommande);
    CommandeDTO ajouterProduitACommande(Long idCommande, Long produitId, int quantite, double prixUnitaire);
    CommandeDTO changerStatutCommande(Long idCommande, StatutCommande nouveauStatut);
}