package org.example.stock_service.dto.response;

public record ProduitVenteResponseDTO(
        Long idProduit,
        String nomProduit,
        double prixVente,
        String globalSku
) {
    public static ProduitVenteResponseDTO from(ProduitResponseDTO product) {
        return new ProduitVenteResponseDTO(
                product.getIdProduit(), product.getNomProduit(),
                product.getPrixVente(), product.getGlobalSku());
    }
}
