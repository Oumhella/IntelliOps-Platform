package org.example.stock_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.common.security.TenantContext;
import org.example.stock_service.dto.request.BoutiqueRequestDTO;
import org.example.stock_service.dto.response.BoutiqueResponseDTO;
import org.example.stock_service.entity.Boutique;
import org.example.stock_service.entity.TypePlateforme;
import org.example.stock_service.mapper.StockMapper;
import org.example.stock_service.repository.BoutiqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoutiqueServiceImpl implements BoutiqueService {

    private final BoutiqueRepository boutiqueRepository;
    private final StockMapper stockMapper;

    @Override
    @Transactional
    public BoutiqueResponseDTO creerBoutique(BoutiqueRequestDTO request) {
        requireManualLocation(request);
        Boutique boutique = stockMapper.toEntity(request);
        boutique.setAdminId(TenantContext.requireUserId());
        boutique.setEnterpriseId(TenantContext.requireEnterpriseId());
        return stockMapper.toResponse(boutiqueRepository.save(boutique));
    }

    @Override
    @Transactional(readOnly = true)
    public BoutiqueResponseDTO obtenirBoutique(Long idBoutique) {
        return stockMapper.toResponse(findBoutique(idBoutique));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoutiqueResponseDTO> obtenirBoutiques() {
        return boutiqueRepository.findAllByEnterpriseIdOrderByNomBoutiqueAsc(TenantContext.requireEnterpriseId())
                .stream()
                .map(stockMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BoutiqueResponseDTO modifierBoutique(Long idBoutique, BoutiqueRequestDTO request) {
        requireManualLocation(request);
        Boutique boutique = findBoutique(idBoutique);
        boutique.setNomBoutique(request.getNomBoutique());
        boutique.setPlateformeType(TypePlateforme.MANUAL);
        return stockMapper.toResponse(boutiqueRepository.save(boutique));
    }

    private Boutique findBoutique(Long idBoutique) {
        return boutiqueRepository.findByIdBoutiqueAndEnterpriseId(idBoutique, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Boutique introuvable : " + idBoutique));
    }

    private void requireManualLocation(BoutiqueRequestDTO request) {
        if (request.getPlateformeType() != TypePlateforme.MANUAL) {
            throw new IllegalStateException(
                    "Stock locations are internal. Connect external stores from the integration workspace.");
        }
    }
}
