package org.example.lead_service.dto;

import lombok.Data;

@Data
public class LignesCommandeDTO {
    private Long idLigne;
    private int quantite;
    private double prixUnitaireApplique;
    private Long produitId;
}