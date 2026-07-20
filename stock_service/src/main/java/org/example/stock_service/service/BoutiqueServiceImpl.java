package org.example.stock_service.service;

import lombok.RequiredArgsConstructor;
import org.example.stock_service.connector.ExternalPlatformConnector;
import org.example.stock_service.connector.PlatformConnectorFactory;
import org.example.stock_service.dto.request.BoutiqueRequestDTO;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.dto.response.BoutiqueResponseDTO;
import org.example.stock_service.entity.*;
import org.example.stock_service.mapper.StockMapper;
import org.example.stock_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BoutiqueServiceImpl implements BoutiqueService {

    private final BoutiqueRepository boutiqueRepository;
    private final ProduitRepository produitRepository;
    private final InventaireRepository inventaireRepository;
    private final StockMapper stockMapper;
    private final PlatformConnectorFactory connectorFactory;

    @Transactional
    @Override
    public BoutiqueResponseDTO creerBoutique(BoutiqueRequestDTO request) {
        Boutique boutique = stockMapper.toEntity(request);
        Boutique saved = boutiqueRepository.save(boutique);
        return stockMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public boolean testerConnexion(Long idBoutique) {
        Boutique boutique = boutiqueRepository.findById(idBoutique)
                .orElseThrow(() -> new EntityNotFoundException("Boutique non trouvée : " + idBoutique));

        ExternalPlatformConnector connector = connectorFactory.getConnector(boutique.getPlateformeType());
        return connector.testerConnexion(boutique.getCleApi());
    }

    @Transactional
    @Override
    public void synchroniserProduits(Long idBoutique) {
        Boutique boutique = boutiqueRepository.findById(idBoutique)
                .orElseThrow(() -> new EntityNotFoundException("Boutique introuvable"));

        // 1. Récupère le bon connecteur (Shopify, WooCommerce, YouCan...)
        ExternalPlatformConnector connector = connectorFactory.getConnector(boutique.getPlateformeType());

        // 2. Importe les produits depuis l'API distante
        List<ProduitRequestDTO> produitsExternes = connector.importerProduits(boutique.getCleApi());

        // 3. Persiste ou met à jour les produits et génère leur inventaire initial
        for (ProduitRequestDTO dto : produitsExternes) {
            Produit produit = produitRepository.findByGlobalSku(dto.getGlobalSku())
                    .orElseGet(() -> produitRepository.save(stockMapper.toEntity(dto)));

            // Crée l'inventaire dans la boutique si non existant
            inventaireRepository.findByBoutiqueIdBoutiqueAndProduitIdProduit(boutique.getIdBoutique(), produit.getIdProduit())
                    .orElseGet(() -> {
                        Inventaire inv = Inventaire.builder()
                                .boutique(boutique)
                                .produit(produit)
                                .quantiteDisponible(0)
                                .quantiteReservee(0)
                                .build();
                        return inventaireRepository.save(inv);
                    });
        }
    }
}