package org.example.stock_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.common.exception.ConflictException;
import org.example.common.security.TenantContext;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.dto.response.ProduitResponseDTO;
import org.example.stock_service.entity.Produit;
import org.example.stock_service.mapper.StockMapper;
import org.example.stock_service.repository.InventaireRepository;
import org.example.stock_service.repository.ProduitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProduitServiceImpl implements ProduitService {

    private final ProduitRepository produitRepository;
    private final InventaireRepository inventaireRepository;
    private final StockMapper stockMapper;

    @Override
    @Transactional
    public ProduitResponseDTO creerProduit(ProduitRequestDTO request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        ensureSkuAvailable(request.getGlobalSku(), enterpriseId, null);
        Produit produit = stockMapper.toEntity(request);
        produit.setEnterpriseId(enterpriseId);
        return stockMapper.toResponse(produitRepository.save(produit));
    }

    @Override
    @Transactional(readOnly = true)
    public ProduitResponseDTO obtenirProduitParId(Long idProduit) {
        return stockMapper.toResponse(findProduct(idProduit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProduitResponseDTO> obtenirTousLesProduits() {
        return produitRepository.findAllByEnterpriseIdOrderByNomProduitAsc(TenantContext.requireEnterpriseId())
                .stream()
                .map(stockMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProduitResponseDTO modifierProduit(Long idProduit, ProduitRequestDTO request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        Produit produit = findProduct(idProduit);
        ensureSkuAvailable(request.getGlobalSku(), enterpriseId, idProduit);
        produit.setNomProduit(request.getNomProduit());
        produit.setPrixAchat(request.getPrixAchat());
        produit.setPrixVente(request.getPrixVente());
        produit.setGlobalSku(request.getGlobalSku());
        return stockMapper.toResponse(produitRepository.save(produit));
    }

    @Override
    @Transactional
    public void supprimerProduit(Long idProduit) {
        Produit produit = findProduct(idProduit);
        if (inventaireRepository.existsByProduitIdProduit(idProduit)) {
            throw new ConflictException("Impossible de supprimer un produit encore utilisÃ© dans un inventaire.");
        }
        produitRepository.delete(produit);
    }

    private Produit findProduct(Long idProduit) {
        return produitRepository.findByIdProduitAndEnterpriseId(idProduit, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Produit introuvable : " + idProduit));
    }

    private void ensureSkuAvailable(String sku, Long enterpriseId, Long currentProductId) {
        produitRepository.findByGlobalSkuAndEnterpriseId(sku, enterpriseId)
                .filter(existing -> currentProductId == null || !existing.getIdProduit().equals(currentProductId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Un produit avec le SKU " + sku + " existe dÃ©jÃ .");
                });
    }
}
