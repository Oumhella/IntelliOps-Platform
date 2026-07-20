package org.example.stock_service.dto.request;

import lombok.Data;

@Data
public class ProduitRequestDTO {
    private String nomProduit;
    private double prixAchat;
    private double prixVente;
    private String globalSku;
}