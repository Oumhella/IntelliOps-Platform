package org.example.stock_service.service;

import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.RegleApprovisionnementRequestDTO;
import org.example.stock_service.dto.request.UpdateStockRequestDTO;
import org.example.stock_service.dto.response.InventaireResponseDTO;
import org.example.stock_service.entity.*;
import org.example.stock_service.mapper.StockMapper;
import org.example.stock_service.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventaireServiceImpl implements InventaireService {

    private final InventaireRepository inventaireRepository;
    private final BoutiqueRepository boutiqueRepository; // <-- Ajouté pour récupérer l'entité Boutique
    private final ProduitRepository produitRepository;   // <-- Ajouté pour récupérer l'entité Produit
    private final StockMapper stockMapper;

    @Transactional
    @Override
    public InventaireResponseDTO ajusterStock(Long idBoutique, Long idProduit, UpdateStockRequestDTO request, Long auteurId) {
        // Auto-création de l'inventaire s'il n'existe pas encore pour cette boutique & produit
        Inventaire inventaire = obtenirOuCreerInventaire(idBoutique, idProduit);

        // Appel de la méthode métier d'encapsulation qui génère le MouvementStock (Audit Note)
        inventaire.updateQuantity(request.getQuantite(), request.getTypeMouvement(), auteurId);

        Inventaire saved = inventaireRepository.save(inventaire);
        return stockMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public InventaireResponseDTO reserverStock(Long idBoutique, Long idProduit, int quantite, Long auteurId) {
        // Pour réserver du stock, l'inventaire DOIT exister
        Inventaire inventaire = trouverInventaireOuLeverException(idBoutique, idProduit);

        // Appel de la méthode métier d'encapsulation pour la réservation
        inventaire.reserveStock(quantite, auteurId);

        Inventaire saved = inventaireRepository.save(inventaire);
        return stockMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public InventaireResponseDTO obtenirInventaireParBoutiqueEtProduit(Long idBoutique, Long idProduit) {
        Inventaire inventaire = trouverInventaireOuLeverException(idBoutique, idProduit);
        return stockMapper.toResponse(inventaire);
    }

    @Transactional(readOnly = true)
    @Override
    public List<InventaireResponseDTO> obtenirInventairesParBoutique(Long idBoutique) {
        return inventaireRepository.findByBoutiqueIdBoutique(idBoutique).stream()
                .map(stockMapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public InventaireResponseDTO configurerRegleApprovisionnement(Long idInventaire, RegleApprovisionnementRequestDTO request) {
        Inventaire inventaire = inventaireRepository.findById(idInventaire)
                .orElseThrow(() -> new EntityNotFoundException("Inventaire introuvable avec l'ID : " + idInventaire));

        if (inventaire.getRegleApprovisionnement() == null) {
            RegleApprovisionnement regle = stockMapper.toEntity(request);
            regle.setInventaire(inventaire);
            inventaire.setRegleApprovisionnement(regle);
        } else {
            inventaire.getRegleApprovisionnement().update(
                    request.getSeuilAlerte(),
                    request.getQuantiteRecommandeAuto(),
                    Boolean.TRUE.equals(request.getEstActif())
            );
        }

        Inventaire saved = inventaireRepository.save(inventaire);
        return stockMapper.toResponse(saved);
    }

    /**
     * Recherche l'inventaire. S'il n'existe pas, vérifie l'existence de la boutique et du produit
     * puis initialise un nouvel inventaire avec une quantité initiale de 0.
     */
    private Inventaire obtenirOuCreerInventaire(Long idBoutique, Long idProduit) {
        return inventaireRepository.findByBoutiqueIdBoutiqueAndProduitIdProduit(idBoutique, idProduit)
                .orElseGet(() -> {
                    Boutique boutique = boutiqueRepository.findById(idBoutique)
                            .orElseThrow(() -> new EntityNotFoundException("Boutique introuvable avec l'ID : " + idBoutique));
                    Produit produit = produitRepository.findById(idProduit)
                            .orElseThrow(() -> new EntityNotFoundException("Produit introuvable avec l'ID : " + idProduit));

                    Inventaire nouvelInventaire = new Inventaire();
                    nouvelInventaire.setBoutique(boutique);
                    nouvelInventaire.setProduit(produit);
                    nouvelInventaire.setQuantiteDisponible(0);
                    nouvelInventaire.setQuantiteReservee(0);
                    return inventaireRepository.save(nouvelInventaire);
                });
    }

    private Inventaire trouverInventaireOuLeverException(Long idBoutique, Long idProduit) {
        return inventaireRepository.findByBoutiqueIdBoutiqueAndProduitIdProduit(idBoutique, idProduit)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inventaire introuvable pour la boutique " + idBoutique + " et le produit " + idProduit));
    }
}