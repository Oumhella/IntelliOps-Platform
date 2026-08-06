package org.example.stock_service.controller;

import lombok.RequiredArgsConstructor;
import org.example.stock_service.dto.request.BoutiqueRequestDTO;
import org.example.stock_service.dto.response.BoutiqueResponseDTO;
import org.example.stock_service.service.BoutiqueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/boutiques")
@RequiredArgsConstructor
public class BoutiqueController {

    private final BoutiqueService boutiqueService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BoutiqueResponseDTO> creerBoutique(@RequestBody BoutiqueRequestDTO request) {
        return new ResponseEntity<>(boutiqueService.creerBoutique(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<BoutiqueResponseDTO>> obtenirBoutiques() {
        return ResponseEntity.ok(boutiqueService.obtenirBoutiques());
    }

    @GetMapping("/{idBoutique}")
    public ResponseEntity<BoutiqueResponseDTO> obtenirBoutique(@PathVariable Long idBoutique) {
        return ResponseEntity.ok(boutiqueService.obtenirBoutique(idBoutique));
    }

    @PutMapping("/{idBoutique}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BoutiqueResponseDTO> modifierBoutique(
            @PathVariable Long idBoutique,
            @RequestBody BoutiqueRequestDTO request) {
        return ResponseEntity.ok(boutiqueService.modifierBoutique(idBoutique, request));
    }

    @PostMapping("/{idBoutique}/tester-connexion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> testerConnexion(@PathVariable Long idBoutique) {
        return ResponseEntity.ok(boutiqueService.testerConnexion(idBoutique));
    }

    @PostMapping("/{idBoutique}/synchroniser")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> synchroniserProduits(@PathVariable Long idBoutique) {
        boutiqueService.synchroniserProduits(idBoutique);
        return ResponseEntity.accepted().build();
    }
}
