package org.example.stock_service.connector;

import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.entity.TypePlateforme;
import java.util.List;

public interface ExternalPlatformConnector {

    TypePlateforme getSupportedPlatform();

    boolean testerConnexion(String cleApi);

    List<ProduitRequestDTO> importerProduits(String cleApi);
}