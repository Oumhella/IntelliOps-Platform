package org.example.stock_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.dto.response.ProduitResponseDTO;
import org.example.stock_service.dto.response.ProduitVenteResponseDTO;
import org.example.stock_service.service.ProduitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/produits")
@RequiredArgsConstructor
public class ProduitController {

    private final ProduitService produitService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC', 'INTEGRATION_SERVICE')")
    public ResponseEntity<ProduitResponseDTO> creerProduit(@Valid @RequestBody ProduitRequestDTO request) {
        return new ResponseEntity<>(produitService.creerProduit(request), HttpStatus.CREATED);
    }

    @GetMapping("/{idProduit}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<ProduitResponseDTO> obtenirProduit(@PathVariable Long idProduit) {
        return ResponseEntity.ok(produitService.obtenirProduitParId(idProduit));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<List<ProduitResponseDTO>> obtenirTousLesProduits() {
        return ResponseEntity.ok(produitService.obtenirTousLesProduits());
    }

    @PutMapping("/{idProduit}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<ProduitResponseDTO> modifierProduit(
            @PathVariable Long idProduit,
            @Valid @RequestBody ProduitRequestDTO request) {
        return ResponseEntity.ok(produitService.modifierProduit(idProduit, request));
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC')")
    public ResponseEntity<List<ProduitVenteResponseDTO>> obtenirCatalogueVente() {
        return ResponseEntity.ok(produitService.obtenirTousLesProduits().stream()
                .map(ProduitVenteResponseDTO::from)
                .toList());
    }

    @GetMapping("/{idProduit}/catalog")
    @PreAuthorize("hasAnyRole('ADMIN', 'CSM', 'LOGISTIC', 'INTEGRATION_SERVICE')")
    public ResponseEntity<ProduitVenteResponseDTO> obtenirProduitVente(@PathVariable Long idProduit) {
        return ResponseEntity.ok(ProduitVenteResponseDTO.from(
                produitService.obtenirProduitParId(idProduit)));
    }

    @DeleteMapping("/{idProduit}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<Void> supprimerProduit(@PathVariable Long idProduit) {
        produitService.supprimerProduit(idProduit);
        return ResponseEntity.noContent().build();
    }
}
