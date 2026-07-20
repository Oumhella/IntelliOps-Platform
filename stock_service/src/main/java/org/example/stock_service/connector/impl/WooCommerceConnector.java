package org.example.stock_service.connector.impl;

import org.example.stock_service.connector.ExternalPlatformConnector;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.entity.TypePlateforme;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WooCommerceConnector implements ExternalPlatformConnector {

    @Override
    public TypePlateforme getSupportedPlatform() {
        return TypePlateforme.WOOCOMMERCE;
    }

    @Override
    public boolean testerConnexion(String cleApi) {
        // Logique RestTemplate / WebClient vers WooCommerce REST API v3
        return cleApi != null && cleApi.contains("ck_");
    }

    @Override
    public List<ProduitRequestDTO> importerProduits(String cleApi) {
        return List.of();
    }
}