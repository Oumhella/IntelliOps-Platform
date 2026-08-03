package org.example.stock_service.service;

import org.example.stock_service.dto.request.BoutiqueRequestDTO;
import org.example.stock_service.dto.response.BoutiqueResponseDTO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BoutiqueService {
    @Transactional
    BoutiqueResponseDTO creerBoutique(BoutiqueRequestDTO request);

    @Transactional(readOnly = true)
    BoutiqueResponseDTO obtenirBoutique(Long idBoutique);

    @Transactional(readOnly = true)
    List<BoutiqueResponseDTO> obtenirBoutiques();

    @Transactional
    BoutiqueResponseDTO modifierBoutique(Long idBoutique, BoutiqueRequestDTO request);

    @Transactional
    boolean testerConnexion(Long idBoutique);

    @Transactional
    void synchroniserProduits(Long idBoutique);
}
