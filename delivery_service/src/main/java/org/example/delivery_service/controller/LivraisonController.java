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
import org.example.common.dto.PageResponse;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;

@RestController
@RequestMapping("/api/v1/livraisons")
@RequiredArgsConstructor
public class LivraisonController {

    private final LivraisonService livraisonService;

    @GetMapping
    public ResponseEntity<PageResponse<LivraisonResponse>> rechercher(
            @RequestParam(required = false) StatutLivraison statut,
            @RequestParam(required = false) TypeTransporteur transporteur,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(livraisonService.search(statut, transporteur, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LivraisonResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(livraisonService.getById(id));
    }

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
