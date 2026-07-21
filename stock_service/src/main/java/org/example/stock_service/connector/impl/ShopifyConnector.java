package org.example.stock_service.connector.impl;

import org.example.stock_service.connector.ExternalPlatformConnector;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.entity.TypePlateforme;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShopifyConnector implements ExternalPlatformConnector {

    @Override
    public TypePlateforme getSupportedPlatform() {
        return TypePlateforme.SHOPIFY;
    }

    @Override
    public boolean testerConnexion(String cleApi) {
        // Logique RestTemplate / WebClient vers https://{shop}.myshopify.com/admin/api/...
        return cleApi != null && cleApi.startsWith("shpat_");
    }

    @Override
    public List<ProduitRequestDTO> importerProduits(String cleApi) {
        // Consomme l'API Shopify et renvoie les DTOs prêts à être insérés
        return List.of();
    }
}