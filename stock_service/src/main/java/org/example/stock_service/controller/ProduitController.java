package org.example.stock_service.controller;

import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.ProduitRequestDTO;
import org.example.stock_service.dto.response.ProduitResponseDTO;
import org.example.stock_service.service.ProduitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/produits")
@RequiredArgsConstructor
public class ProduitController {

    private final ProduitService produitService;

    @PostMapping
    public ResponseEntity<ProduitResponseDTO> creerProduit(@RequestBody ProduitRequestDTO request) {
        return new ResponseEntity<>(produitService.creerProduit(request), HttpStatus.CREATED);
    }

    @GetMapping("/{idProduit}")
    public ResponseEntity<ProduitResponseDTO> obtenirProduit(@PathVariable Long idProduit) {
        return ResponseEntity.ok(produitService.obtenirProduitParId(idProduit));
    }

    @GetMapping
    public ResponseEntity<List<ProduitResponseDTO>> obtenirTousLesProduits() {
        return ResponseEntity.ok(produitService.obtenirTousLesProduits());
    }

    @PutMapping("/{idProduit}")
    public ResponseEntity<ProduitResponseDTO> modifierProduit(
            @PathVariable Long idProduit,
            @RequestBody ProduitRequestDTO request) {
        return ResponseEntity.ok(produitService.modifierProduit(idProduit, request));
    }
}