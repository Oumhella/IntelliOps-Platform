package org.example.lead_service.dto;

import lombok.Data;
import org.example.lead_service.entity.StatutCommande;
import org.example.lead_service.entity.StatutPaiementCommande;

import java.util.List;
import java.time.LocalDateTime;

@Data
public class CommandeDTO {
    private Long idCommande;
    private String reference;
    private double totalPrix;
    private StatutCommande statutCommande;
    private CoordonneesClientDTO infosClient;
    private List<LignesCommandeDTO> lignesCommande;
    private Long stockLocationId;
    private LocalDateTime createdAt;
    private StatutPaiementCommande statutPaiement;
}
