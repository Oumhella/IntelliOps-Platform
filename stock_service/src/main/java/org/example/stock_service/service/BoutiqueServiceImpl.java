package org.example.stock_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.common.security.TenantContext;
import org.example.stock_service.connector.ExternalPlatformConnector;
import org.example.stock_service.connector.PlatformConnectorFactory;
import org.example.stock_service.dto.request.BoutiqueRequestDTO;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.dto.response.BoutiqueResponseDTO;
import org.example.stock_service.entity.Boutique;
import org.example.stock_service.entity.Inventaire;
import org.example.stock_service.entity.Produit;
import org.example.stock_service.mapper.StockMapper;
import org.example.stock_service.repository.BoutiqueRepository;
import org.example.stock_service.repository.InventaireRepository;
import org.example.stock_service.repository.ProduitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoutiqueServiceImpl implements BoutiqueService {

    private final BoutiqueRepository boutiqueRepository;
    private final ProduitRepository produitRepository;
    private final InventaireRepository inventaireRepository;
    private final StockMapper stockMapper;
    private final PlatformConnectorFactory connectorFactory;

    @Override
    @Transactional
    public BoutiqueResponseDTO creerBoutique(BoutiqueRequestDTO request) {
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
        Boutique boutique = findBoutique(idBoutique);
        boutique.setNomBoutique(request.getNomBoutique());
        boutique.setPlateformeType(request.getPlateformeType());
        boutique.setCleApi(request.getCleApi());
        return stockMapper.toResponse(boutiqueRepository.save(boutique));
    }

    @Override
    @Transactional
    public boolean testerConnexion(Long idBoutique) {
        Boutique boutique = findBoutique(idBoutique);
        ExternalPlatformConnector connector = connectorFactory.getConnector(boutique.getPlateformeType());
        return connector.testerConnexion(boutique.getCleApi());
    }

    @Override
    @Transactional
    public void synchroniserProduits(Long idBoutique) {
        Boutique boutique = findBoutique(idBoutique);
        Long enterpriseId = TenantContext.requireEnterpriseId();
        ExternalPlatformConnector connector = connectorFactory.getConnector(boutique.getPlateformeType());
        List<ProduitRequestDTO> produitsExternes = connector.importerProduits(boutique.getCleApi());

        for (ProduitRequestDTO dto : produitsExternes) {
            Produit produit = produitRepository.findByGlobalSkuAndEnterpriseId(dto.getGlobalSku(), enterpriseId)
                    .orElseGet(() -> {
                        Produit created = stockMapper.toEntity(dto);
                        created.setEnterpriseId(enterpriseId);
                        return produitRepository.save(created);
                    });

            inventaireRepository.findByBoutiqueIdBoutiqueAndProduitIdProduitAndBoutiqueEnterpriseId(
                            boutique.getIdBoutique(), produit.getIdProduit(), enterpriseId)
                    .orElseGet(() -> inventaireRepository.save(Inventaire.builder()
                            .boutique(boutique)
                            .produit(produit)
                            .quantiteDisponible(0)
                            .quantiteReservee(0)
                            .build()));
        }
    }

    private Boutique findBoutique(Long idBoutique) {
        return boutiqueRepository.findByIdBoutiqueAndEnterpriseId(idBoutique, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Boutique introuvable : " + idBoutique));
    }
}
