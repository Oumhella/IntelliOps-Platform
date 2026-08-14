package org.example.stock_service.service;

import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.RegleApprovisionnementRequestDTO;
import org.example.stock_service.dto.request.UpdateStockRequestDTO;
import org.example.stock_service.dto.request.ReservationStockRequest;
import org.example.stock_service.dto.response.InventaireResponseDTO;
import org.example.stock_service.entity.*;
import org.example.stock_service.mapper.StockMapper;
import org.example.stock_service.repository.*;
import org.example.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.example.stock_service.event.StockAlertProducer;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventaireServiceImpl implements InventaireService {

    private final InventaireRepository inventaireRepository;
    private final ReservationStockRepository reservationStockRepository;
    private final BoutiqueRepository boutiqueRepository; // <-- Ajouté pour récupérer l'entité Boutique
    private final ProduitRepository produitRepository;   // <-- Ajouté pour récupérer l'entité Produit
    private final StockMapper stockMapper;
    private final StockAlertProducer stockAlertProducer;

    @Transactional
    @Override
    public InventaireResponseDTO ajusterStock(Long idBoutique, Long idProduit, UpdateStockRequestDTO request, Long auteurId) {
        validateManualMovement(request);
        // Auto-création de l'inventaire s'il n'existe pas encore pour cette boutique & produit
        Inventaire inventaire = obtenirOuCreerInventaire(idBoutique, idProduit);

        // Appel de la méthode métier d'encapsulation qui génère le MouvementStock (Audit Note)
        inventaire.updateQuantity(request.getQuantite(), request.getTypeMouvement(), auteurId);

        Inventaire saved = inventaireRepository.save(inventaire);
        evaluateReplenishment(saved);
        return stockMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public InventaireResponseDTO reserverStock(
            Long idBoutique, Long idProduit, ReservationStockRequest request, Long auteurId) {
        // Pour réserver du stock, l'inventaire DOIT exister
        Inventaire inventaire = trouverInventaireOuLeverException(idBoutique, idProduit);

        // Appel de la méthode métier d'encapsulation pour la réservation
        String reference = request.referenceOperation().trim();
        Long enterpriseId = TenantContext.requireEnterpriseId();
        var existing = reservationStockRepository
                .findByEnterpriseIdAndReferenceOperationAndProduitId(enterpriseId, reference, idProduit);
        if (existing.isPresent()) {
            ReservationStock reservation = existing.get();
            if (!reservation.getInventaire().getId().equals(inventaire.getId())
                    || reservation.getQuantite() != request.quantite()) {
                throw new IllegalStateException(
                        "La reference de reservation existe deja avec un autre emplacement ou une autre quantite.");
            }
            return stockMapper.toResponse(inventaire);
        }

        inventaire.reserveStock(request.quantite(), auteurId);

        Inventaire saved = inventaireRepository.save(inventaire);
        reservationStockRepository.save(ReservationStock.builder()
                .enterpriseId(enterpriseId)
                .referenceOperation(reference)
                .produitId(idProduit)
                .quantite(request.quantite())
                .statut(StatutReservationStock.RESERVED)
                .inventaire(saved)
                .auteurId(auteurId)
                .build());
        evaluateReplenishment(saved);
        return stockMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public InventaireResponseDTO libererReservation(
            Long idBoutique, Long idProduit, ReservationStockRequest request, Long auteurId) {
        ReservationStock reservation = findReservation(idBoutique, idProduit, request);
        if (reservation.getStatut() == StatutReservationStock.RESERVED) {
            reservation.getInventaire().releaseReservation(reservation.getQuantite(), auteurId);
            reservation.setStatut(StatutReservationStock.RELEASED);
            reservationStockRepository.save(reservation);
            inventaireRepository.save(reservation.getInventaire());
            evaluateReplenishment(reservation.getInventaire());
        }
        return stockMapper.toResponse(reservation.getInventaire());
    }

    @Transactional
    @Override
    public InventaireResponseDTO consommerReservation(
            Long idBoutique, Long idProduit, ReservationStockRequest request, Long auteurId) {
        ReservationStock reservation = findReservation(idBoutique, idProduit, request);
        if (reservation.getStatut() == StatutReservationStock.RELEASED) {
            throw new IllegalStateException("Une reservation liberee ne peut pas etre consommee.");
        }
        if (reservation.getStatut() == StatutReservationStock.RESERVED) {
            reservation.getInventaire().consumeReservation(reservation.getQuantite(), auteurId);
            reservation.setStatut(StatutReservationStock.CONSUMED);
            reservationStockRepository.save(reservation);
            inventaireRepository.save(reservation.getInventaire());
            evaluateReplenishment(reservation.getInventaire());
        }
        return stockMapper.toResponse(reservation.getInventaire());
    }

    @Transactional
    @Override
    public InventaireResponseDTO obtenirInventaireParBoutiqueEtProduit(Long idBoutique, Long idProduit) {
        Inventaire inventaire = trouverInventaireOuLeverException(idBoutique, idProduit);
        return stockMapper.toResponse(inventaire);
    }

    @Transactional(readOnly = true)
    @Override
    public List<InventaireResponseDTO> obtenirInventairesParBoutique(Long idBoutique) {
        return inventaireRepository.findByBoutiqueIdBoutiqueAndBoutiqueEnterpriseId(
                        idBoutique, TenantContext.requireEnterpriseId()).stream()
                .map(stockMapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public InventaireResponseDTO configurerRegleApprovisionnement(Long idInventaire, RegleApprovisionnementRequestDTO request) {
        Inventaire inventaire = inventaireRepository.findByIdAndBoutiqueEnterpriseId(
                        idInventaire, TenantContext.requireEnterpriseId())
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
        evaluateReplenishment(saved);
        return stockMapper.toResponse(saved);
    }

    private void evaluateReplenishment(Inventaire inventory) {
        RegleApprovisionnement rule=inventory.getRegleApprovisionnement();
        if(rule==null||!Boolean.TRUE.equals(rule.getEstActif())) return;
        if(inventory.getQuantiteDisponible()>rule.getSeuilAlerte()){
            rule.setAlerteDeclenchee(false);
            return;
        }
        if(Boolean.TRUE.equals(rule.getAlerteDeclenchee())) return;
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        String recipient=authentication==null?null:authentication.getName();
        stockAlertProducer.send(inventory.getBoutique().getEnterpriseId(),recipient,
                inventory.getProduit().getNomProduit(),inventory.getBoutique().getNomBoutique(),
                inventory.getQuantiteDisponible(),rule.getSeuilAlerte(),rule.getQuantiteRecommandeAuto());
        rule.setAlerteDeclenchee(true);
    }

    /**
     * Recherche l'inventaire. S'il n'existe pas, vérifie l'existence de la boutique et du produit
     * puis initialise un nouvel inventaire avec une quantité initiale de 0.
     */
    private Inventaire obtenirOuCreerInventaire(Long idBoutique, Long idProduit) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        return inventaireRepository.findFirstByBoutiqueIdBoutiqueAndProduitIdProduitAndBoutiqueEnterpriseIdOrderByIdAsc(
                        idBoutique, idProduit, enterpriseId)
                .orElseGet(() -> {
                    Boutique boutique = boutiqueRepository.findByIdBoutiqueAndEnterpriseId(idBoutique, enterpriseId)
                            .orElseThrow(() -> new EntityNotFoundException("Emplacement de stock introuvable."));
                    Produit produit = produitRepository.findByIdProduitAndEnterpriseId(idProduit, enterpriseId)
                            .orElseThrow(() -> new EntityNotFoundException("Produit introuvable."));

                    Inventaire nouvelInventaire = new Inventaire();
                    nouvelInventaire.setBoutique(boutique);
                    nouvelInventaire.setProduit(produit);
                    nouvelInventaire.setQuantiteDisponible(0);
                    nouvelInventaire.setQuantiteReservee(0);
                    return inventaireRepository.save(nouvelInventaire);
                });
    }

    private Inventaire trouverInventaireOuLeverException(Long idBoutique, Long idProduit) {
        return inventaireRepository.findFirstByBoutiqueIdBoutiqueAndProduitIdProduitAndBoutiqueEnterpriseIdOrderByIdAsc(
                        idBoutique, idProduit, TenantContext.requireEnterpriseId())
                .orElseGet(() -> obtenirOuCreerInventaire(idBoutique, idProduit));
    }

    private ReservationStock findReservation(
            Long idBoutique, Long idProduit, ReservationStockRequest request) {
        Inventaire inventaire = trouverInventaireOuLeverException(idBoutique, idProduit);
        ReservationStock reservation = reservationStockRepository
                .findByEnterpriseIdAndReferenceOperationAndProduitId(
                        TenantContext.requireEnterpriseId(), request.referenceOperation().trim(), idProduit)
                .orElseThrow(() -> new EntityNotFoundException("Reservation de stock introuvable."));
        if (!reservation.getInventaire().getId().equals(inventaire.getId())
                || reservation.getQuantite() != request.quantite()) {
            throw new IllegalStateException("La reservation ne correspond pas a la commande demandee.");
        }
        return reservation;
    }

    private void validateManualMovement(UpdateStockRequestDTO request) {
        if (request == null || request.getTypeMouvement() == null || request.getQuantite() == 0) {
            throw new IllegalArgumentException("Un mouvement et une quantite non nulle sont requis.");
        }
        switch (request.getTypeMouvement()) {
            case REASSORT, RETOUR -> {
                if (request.getQuantite() < 0) {
                    throw new IllegalArgumentException("Ce mouvement doit augmenter le stock.");
                }
            }
            case PERTE -> {
                if (request.getQuantite() > 0) {
                    throw new IllegalArgumentException("Une perte doit diminuer le stock.");
                }
            }
            case AJUSTEMENT -> {
                // A signed delta is valid for a manually justified adjustment.
            }
            case VENTE, RESERVATION, LIBERATION -> throw new IllegalArgumentException(
                    "Ce type de mouvement est gere uniquement par le cycle de vie des commandes.");
        }
    }
}
