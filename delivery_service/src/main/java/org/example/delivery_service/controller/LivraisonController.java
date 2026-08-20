package org.example.delivery_service.controller;

import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.UpdateStatutRequest;
import org.example.delivery_service.dto.request.AssignCourierRequest;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.example.delivery_service.dto.request.CompleteDeliveryRequest;
import org.example.delivery_service.dto.request.FailedDeliveryAttemptRequest;
import org.example.delivery_service.dto.response.CourierDashboardResponse;
import org.example.delivery_service.dto.response.ProofPhotoResponse;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.CacheControl;

@RestController
@RequestMapping("/api/v1/livraisons")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC', 'LIVREUR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<LivraisonResponse> confirmerReception(@PathVariable Long id) {
        return ResponseEntity.ok(livraisonService.confirmerReception(id));
    }

    @PatchMapping("/{id}/livreur")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<LivraisonResponse> assignerLivreur(
            @PathVariable Long id, @Valid @RequestBody AssignCourierRequest request) {
        return ResponseEntity.ok(livraisonService.assignerLivreur(id, request));
    }

    @GetMapping("/me/dashboard")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<CourierDashboardResponse> courierDashboard() {
        return ResponseEntity.ok(livraisonService.courierDashboard());
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<LivraisonResponse> accept(@PathVariable Long id) {
        return ResponseEntity.ok(livraisonService.acceptAssignment(id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<LivraisonResponse> start(@PathVariable Long id) {
        return ResponseEntity.ok(livraisonService.startDelivery(id));
    }

    @PostMapping("/{id}/failed-attempt")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<LivraisonResponse> failedAttempt(
            @PathVariable Long id, @Valid @RequestBody FailedDeliveryAttemptRequest request) {
        return ResponseEntity.ok(livraisonService.reportFailedAttempt(id, request));
    }

    @PostMapping("/{id}/request-return")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<LivraisonResponse> requestReturn(@PathVariable Long id) {
        return ResponseEntity.ok(livraisonService.requestReturn(id));
    }

    @PostMapping(value = "/{id}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<LivraisonResponse> complete(
            @PathVariable Long id,
            @Valid @RequestPart("details") CompleteDeliveryRequest request,
            @RequestPart(value = "proofPhoto", required = false) MultipartFile proofPhoto) {
        return ResponseEntity.ok(livraisonService.completeDelivery(id, request, proofPhoto));
    }

    @PostMapping("/{id}/reconcile-cod")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC')")
    public ResponseEntity<LivraisonResponse> reconcileCod(@PathVariable Long id) {
        return ResponseEntity.ok(livraisonService.reconcileCod(id));
    }

    @GetMapping("/{id}/proof-photo")
    public ResponseEntity<byte[]> proofPhoto(@PathVariable Long id) {
        ProofPhotoResponse proof = livraisonService.getProofPhoto(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .body(proof.content());
    }
}
