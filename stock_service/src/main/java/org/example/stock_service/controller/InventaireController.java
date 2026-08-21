package org.example.stock_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.RegleApprovisionnementRequestDTO;
import org.example.stock_service.dto.request.UpdateStockRequestDTO;
import org.example.stock_service.dto.request.ReservationStockRequest;
import org.example.stock_service.dto.response.InventaireResponseDTO;
import org.example.stock_service.service.InventaireService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.example.common.security.TenantContext;

@RestController
@RequestMapping("/api/v1/inventaires")
@RequiredArgsConstructor
public class InventaireController {

    private final InventaireService inventaireService;

    @PatchMapping("/boutiques/{idBoutique}/produits/{idProduit}/ajuster")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<InventaireResponseDTO> ajusterStock(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit,
            @RequestBody UpdateStockRequestDTO request) {

        Long auteurId = TenantContext.requireUserId();
        return ResponseEntity.ok(inventaireService.ajusterStock(idBoutique, idProduit, request, auteurId));
    }

    @PostMapping("/boutiques/{idBoutique}/produits/{idProduit}/reserver")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC', 'INTEGRATION_SERVICE')")
    public ResponseEntity<InventaireResponseDTO> reserverStock(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit,
            @Valid @RequestBody ReservationStockRequest request) {

        return ResponseEntity.ok(inventaireService.reserverStock(
                idBoutique, idProduit, request, TenantContext.requireUserId()));
    }

    @PostMapping("/boutiques/{idBoutique}/produits/{idProduit}/liberer")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC', 'INTEGRATION_SERVICE')")
    public ResponseEntity<InventaireResponseDTO> libererReservation(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit,
            @Valid @RequestBody ReservationStockRequest request) {
        return ResponseEntity.ok(inventaireService.libererReservation(
                idBoutique, idProduit, request, TenantContext.requireUserId()));
    }

    @PostMapping("/boutiques/{idBoutique}/produits/{idProduit}/consommer")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC', 'INTEGRATION_SERVICE')")
    public ResponseEntity<InventaireResponseDTO> consommerReservation(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit,
            @Valid @RequestBody ReservationStockRequest request) {
        return ResponseEntity.ok(inventaireService.consommerReservation(
                idBoutique, idProduit, request, TenantContext.requireUserId()));
    }

    @GetMapping("/boutiques/{idBoutique}/produits/{idProduit}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC', 'INTEGRATION_SERVICE')")
    public ResponseEntity<InventaireResponseDTO> obtenirInventaire(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit) {
        return ResponseEntity.ok(inventaireService.obtenirInventaireParBoutiqueEtProduit(idBoutique, idProduit));
    }

    @GetMapping("/boutiques/{idBoutique}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC', 'INTEGRATION_SERVICE')")
    public ResponseEntity<List<InventaireResponseDTO>> obtenirInventairesParBoutique(@PathVariable Long idBoutique) {
        return ResponseEntity.ok(inventaireService.obtenirInventairesParBoutique(idBoutique));
    }

    @PutMapping("/{idInventaire}/regle-approvisionnement")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<InventaireResponseDTO> configurerRegleApprovisionnement(
            @PathVariable Long idInventaire,
            @RequestBody RegleApprovisionnementRequestDTO request) {
        return ResponseEntity.ok(inventaireService.configurerRegleApprovisionnement(idInventaire, request));
    }

}
