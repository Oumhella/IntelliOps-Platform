package org.example.stock_service.service;

import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.dto.response.ProduitResponseDTO;
import org.example.stock_service.entity.Produit;
import org.example.stock_service.mapper.StockMapper;
import org.example.stock_service.repository.ProduitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProduitServiceImpl implements ProduitService {

    private final ProduitRepository produitRepository;
    private final StockMapper stockMapper;

    @Transactional
    @Override
    public ProduitResponseDTO creerProduit(ProduitRequestDTO request) {
        if (produitRepository.findByGlobalSku(request.getGlobalSku()).isPresent()) {
            throw new IllegalArgumentException("Un produit avec le SKU " + request.getGlobalSku() + " existe déjà.");
        }
        Produit produit = stockMapper.toEntity(request);
        Produit saved = produitRepository.save(produit);
        return stockMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public ProduitResponseDTO obtenirProduitParId(Long idProduit) {
        Produit produit = produitRepository.findById(idProduit)
                .orElseThrow(() -> new EntityNotFoundException("Produit introuvable avec l'ID : " + idProduit));
        return stockMapper.toResponse(produit);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProduitResponseDTO> obtenirTousLesProduits() {
        return produitRepository.findAll().stream()
                .map(stockMapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public ProduitResponseDTO modifierProduit(Long idProduit, ProduitRequestDTO request) {
        Produit produit = produitRepository.findById(idProduit)
                .orElseThrow(() -> new EntityNotFoundException("Produit introuvable : " + idProduit));

        produit.setNomProduit(request.getNomProduit());
        produit.setPrixAchat(request.getPrixAchat());
        produit.setPrixVente(request.getPrixVente());
        produit.setGlobalSku(request.getGlobalSku());

        Produit updated = produitRepository.save(produit);
        return stockMapper.toResponse(updated);
    }
}