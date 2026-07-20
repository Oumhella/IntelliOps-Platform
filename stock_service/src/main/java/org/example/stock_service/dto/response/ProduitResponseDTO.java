package org.example.stock_service.dto.response;

import lombok.Data;

@Data
public class ProduitResponseDTO {
    private Long idProduit;
    private String nomProduit;
    private double prixAchat;
    private double prixVente;
    private String globalSku;
}