package org.example.stock_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.common.security.TenantContext;
import org.example.stock_service.dto.request.ExternalProductImportRequest;
import org.example.stock_service.dto.response.ExternalProductImportResponse;
import org.example.stock_service.entity.Inventaire;
import org.example.stock_service.entity.Produit;
import org.example.stock_service.entity.TypeMouvement;
import org.example.stock_service.repository.BoutiqueRepository;
import org.example.stock_service.repository.InventaireRepository;
import org.example.stock_service.repository.ProduitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExternalCatalogImportService {
    private final ProduitRepository produitRepository;
    private final BoutiqueRepository boutiqueRepository;
    private final InventaireRepository inventaireRepository;

    @Transactional
    public ExternalProductImportResponse importProduct(ExternalProductImportRequest request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        String sku = request.sku().trim();

        Produit product = request.internalProductId() == null
                ? produitRepository.findByGlobalSkuAndEnterpriseId(sku, enterpriseId).orElse(null)
                : produitRepository.findByIdProduitAndEnterpriseId(request.internalProductId(), enterpriseId)
                        .orElseThrow(() -> new EntityNotFoundException("Produit lie introuvable."));
        boolean productCreated = product == null;
        if (productCreated) {
            product = Produit.builder()
                    .enterpriseId(enterpriseId)
                    .globalSku(sku)
                    .nomProduit(request.name().trim())
                    .prixAchat(0.0)
                    .prixVente(request.salePrice().doubleValue())
                    .build();
        } else {
            product.setNomProduit(request.name().trim());
            product.setPrixVente(request.salePrice().doubleValue());
            Produit skuOwner = produitRepository.findByGlobalSkuAndEnterpriseId(sku, enterpriseId).orElse(null);
            if (skuOwner == null || skuOwner.getIdProduit().equals(product.getIdProduit())) {
                product.setGlobalSku(sku);
            }
        }
        product = produitRepository.save(product);

        Inventaire inventory = inventaireRepository
                .findFirstByBoutiqueIdBoutiqueAndProduitIdProduitAndBoutiqueEnterpriseIdOrderByIdAsc(
                        request.stockLocationId(), product.getIdProduit(), enterpriseId)
                .orElse(null);
        boolean inventoryCreated = inventory == null && request.availableQuantity() != null;
        if (inventoryCreated) {
            var location = boutiqueRepository
                    .findByIdBoutiqueAndEnterpriseId(request.stockLocationId(), enterpriseId)
                    .orElseThrow(() -> new EntityNotFoundException("Emplacement de stock introuvable."));
            inventory = new Inventaire();
            inventory.setBoutique(location);
            inventory.setProduit(product);
            inventory.setQuantiteDisponible(0);
            inventory.setQuantiteReservee(0);
            if (request.availableQuantity() > 0) {
                inventory.updateQuantity(request.availableQuantity(), TypeMouvement.AJUSTEMENT, 0L);
            }
            inventory = inventaireRepository.save(inventory);
        } else if (inventory != null && request.availableQuantity() != null
                && inventory.getQuantiteDisponible() == 0
                && inventory.getQuantiteReservee() == 0
                && inventory.getMouvements().isEmpty()) {
            // Backfill legacy imports that were created with the former hardcoded zero.
            // Once a location has stock movements or reservations, IntelliOps remains
            // authoritative and a catalogue refresh must never overwrite that lifecycle.
            if (request.availableQuantity() > 0) {
                inventory.updateQuantity(request.availableQuantity(), TypeMouvement.AJUSTEMENT, 0L);
                inventory = inventaireRepository.save(inventory);
            }
        }

        return new ExternalProductImportResponse(product.getIdProduit(), productCreated, inventoryCreated,
                inventory == null ? null : inventory.getQuantiteDisponible());
    }
}
