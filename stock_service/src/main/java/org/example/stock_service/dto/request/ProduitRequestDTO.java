package org.example.stock_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProduitRequestDTO {
    @NotBlank
    @Size(max = 180)
    private String nomProduit;
    @PositiveOrZero
    private double prixAchat;
    @Positive
    private double prixVente;
    @NotBlank
    @Size(max = 100)
    private String globalSku;
}
