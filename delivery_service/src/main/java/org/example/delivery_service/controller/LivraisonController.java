package org.example.delivery_service.controller;

import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.UpdateStatutRequest;
import org.example.delivery_service.dto.response.LivraisonResponse;
import org.example.delivery_service.service.LivraisonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/livraisons")
@RequiredArgsConstructor
public class LivraisonController {

    private final LivraisonService livraisonService;

    @PostMapping("/expedier")
    public ResponseEntity<LivraisonResponse> expedier(@Valid @RequestBody ExpedierLivraisonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(livraisonService.expedierLivraison(request));
    }

    @GetMapping("/tracking/{trackingNum}")
    public ResponseEntity<LivraisonResponse> getByTracking(@PathVariable String trackingNum) {
        return ResponseEntity.ok(livraisonService.getByTrackingNumber(trackingNum));
    }

    @GetMapping("/commande/{commandeId}")
    public ResponseEntity<LivraisonResponse> getByCommande(@PathVariable Long commandeId) {
        return ResponseEntity.ok(livraisonService.getByCommandeId(commandeId));
    }

    @PatchMapping("/{id}/statut")
    public ResponseEntity<LivraisonResponse> mettreAJourStatut(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatutRequest request) {
        return ResponseEntity.ok(livraisonService.mettreAJourStatut(id, request));
    }

    @PostMapping("/{id}/confirmer-reception")
    public ResponseEntity<LivraisonResponse> confirmerReception(@PathVariable Long id) {
        return ResponseEntity.ok(livraisonService.confirmerReception(id));
    }
}