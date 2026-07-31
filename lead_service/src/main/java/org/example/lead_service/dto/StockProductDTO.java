package org.example.lead_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockProductDTO {
    private Long idProduit;
    private String nomProduit;
    private double prixAchat;
    private double prixVente;
    private String globalSku;
}
