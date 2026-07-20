package org.example.stock_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.RegleApprovisionnementRequestDTO;
import org.example.stock_service.dto.request.UpdateStockRequestDTO;
import org.example.stock_service.dto.response.InventaireResponseDTO;
import org.example.stock_service.service.InventaireService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventaires")
@RequiredArgsConstructor
public class InventaireController {

    private final InventaireService inventaireService;

    @PatchMapping("/boutiques/{idBoutique}/produits/{idProduit}/ajuster")
    public ResponseEntity<InventaireResponseDTO> ajusterStock(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit,
            @RequestBody UpdateStockRequestDTO request,
            HttpServletRequest httpRequest) {

        Long auteurId = extraireAuteurId(httpRequest);
        return ResponseEntity.ok(inventaireService.ajusterStock(idBoutique, idProduit, request, auteurId));
    }

    @PostMapping("/boutiques/{idBoutique}/produits/{idProduit}/reserver")
    public ResponseEntity<InventaireResponseDTO> reserverStock(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit,
            @RequestParam int quantite,
            HttpServletRequest httpRequest) {

        Long auteurId = extraireAuteurId(httpRequest);
        return ResponseEntity.ok(inventaireService.reserverStock(idBoutique, idProduit, quantite, auteurId));
    }

    @GetMapping("/boutiques/{idBoutique}/produits/{idProduit}")
    public ResponseEntity<InventaireResponseDTO> obtenirInventaire(
            @PathVariable Long idBoutique,
            @PathVariable Long idProduit) {
        return ResponseEntity.ok(inventaireService.obtenirInventaireParBoutiqueEtProduit(idBoutique, idProduit));
    }

    @GetMapping("/boutiques/{idBoutique}")
    public ResponseEntity<List<InventaireResponseDTO>> obtenirInventairesParBoutique(@PathVariable Long idBoutique) {
        return ResponseEntity.ok(inventaireService.obtenirInventairesParBoutique(idBoutique));
    }

    @PutMapping("/{idInventaire}/regle-approvisionnement")
    public ResponseEntity<InventaireResponseDTO> configurerRegleApprovisionnement(
            @PathVariable Long idInventaire,
            @RequestBody RegleApprovisionnementRequestDTO request) {
        return ResponseEntity.ok(inventaireService.configurerRegleApprovisionnement(idInventaire, request));
    }

    private Long extraireAuteurId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr != null && !userIdStr.isBlank()) {
            return Long.valueOf(userIdStr);
        }
        return 1L; // Valeur par défaut
    }
}