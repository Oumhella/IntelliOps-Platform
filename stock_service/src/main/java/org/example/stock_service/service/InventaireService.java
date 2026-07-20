package org.example.stock_service.service;

import org.example.stock_service.dto.request.RegleApprovisionnementRequestDTO;
import org.example.stock_service.dto.request.UpdateStockRequestDTO;
import org.example.stock_service.dto.response.InventaireResponseDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface InventaireService {
    @Transactional
    InventaireResponseDTO ajusterStock(Long idBoutique, Long idProduit, UpdateStockRequestDTO request, Long auteurId);

    @Transactional
    InventaireResponseDTO reserverStock(Long idBoutique, Long idProduit, int quantite, Long auteurId);

    @Transactional(readOnly = true)
    InventaireResponseDTO obtenirInventaireParBoutiqueEtProduit(Long idBoutique, Long idProduit);

    @Transactional(readOnly = true)
    List<InventaireResponseDTO> obtenirInventairesParBoutique(Long idBoutique);

    @Transactional
    InventaireResponseDTO configurerRegleApprovisionnement(Long idInventaire, RegleApprovisionnementRequestDTO request);
}
