package org.example.stock_service.service;

import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.dto.response.ProduitResponseDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProduitService {
    @Transactional
    ProduitResponseDTO creerProduit(ProduitRequestDTO request);

    @Transactional(readOnly = true)
    ProduitResponseDTO obtenirProduitParId(Long idProduit);

    @Transactional(readOnly = true)
    List<ProduitResponseDTO> obtenirTousLesProduits();

    @Transactional
    ProduitResponseDTO modifierProduit(Long idProduit, ProduitRequestDTO request);
}
