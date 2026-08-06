package org.example.abonnement_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.abonnement_service.dto.request.AbonnementRequest;
import org.example.abonnement_service.dto.request.PaymentCheckoutRequest;
import org.example.abonnement_service.dto.request.SubscriptionCheckoutRequest;
import org.example.abonnement_service.dto.request.UpgradeCheckoutRequest;
import org.example.abonnement_service.dto.request.CompletePaymentCheckoutRequest;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.example.abonnement_service.dto.response.CheckoutPreparationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.abonnement_service.service.AbonnementService;

import java.util.List;
import org.example.common.dto.PageResponse;
import org.example.abonnement_service.entity.StatutAbonnement;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/abonnements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AbonnementController {

    private final AbonnementService abonnementService;

    @GetMapping
    public ResponseEntity<PageResponse<AbonnementResponse>> rechercher(
            @RequestParam(required = false) StatutAbonnement statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(abonnementService.search(statut, page, size));
    }

    /**
     * Souscrire à un nouvel abonnement.
     */
    @PostMapping
    public ResponseEntity<AbonnementResponse> souscrire(@Valid @RequestBody AbonnementRequest request) {
        AbonnementResponse response = abonnementService.souscrire(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Authoritative paid checkout: the backend reads the plan price, captures
     * the card payment, consumes it once, and only then activates the plan.
     */
    @PostMapping("/checkout/prepare")
    public ResponseEntity<CheckoutPreparationResponse> prepareCheckout(
            @Valid @RequestBody SubscriptionCheckoutRequest request) {
        return new ResponseEntity<>(abonnementService.prepareCheckout(request), HttpStatus.CREATED);
    }

    @PostMapping("/checkout/complete")
    public ResponseEntity<AbonnementResponse> completeCheckout(
            @Valid @RequestBody CompletePaymentCheckoutRequest request) {
        return new ResponseEntity<>(abonnementService.completeCheckout(request), HttpStatus.CREATED);
    }

    /**
     * Récupérer un abonnement par son ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AbonnementResponse> getAbonnementById(@PathVariable Long id) {
        return ResponseEntity.ok(abonnementService.getAbonnementById(id));
    }

    /**
     * Récupérer tout l'historique des abonnements d'un utilisateur.
     */
    @GetMapping("/utilisateur/{userId}")
    public ResponseEntity<List<AbonnementResponse>> getHistoriqueUtilisateur(@PathVariable Long userId) {
        return ResponseEntity.ok(abonnementService.getHistoriqueUtilisateur(userId));
    }

    /**
     * Suspendre temporairement un abonnement actif.
     */
    @PostMapping("/{id}/suspendre")
    public ResponseEntity<Void> suspendre(
            @PathVariable Long id,
            @RequestParam String motif) {
        abonnementService.suspendre(id, motif);
        return ResponseEntity.noContent().build();
    }

    /**
     * Renouveler un abonnement (après paiement réussi).
     */
    @PostMapping("/{id}/renouveler")
    public ResponseEntity<AbonnementResponse> renouveler(
            @PathVariable Long id,
            @RequestParam Long paiementId) {
        return ResponseEntity.ok(abonnementService.renouveler(id, paiementId));
    }

    @PostMapping("/{id}/renew-checkout/prepare")
    public ResponseEntity<CheckoutPreparationResponse> prepareRenewalCheckout(
            @PathVariable Long id,
            @Valid @RequestBody PaymentCheckoutRequest request) {
        return ResponseEntity.ok(abonnementService.prepareRenewalCheckout(id, request));
    }

    @PostMapping("/{id}/renew-checkout/complete")
    public ResponseEntity<AbonnementResponse> completeRenewalCheckout(
            @PathVariable Long id,
            @Valid @RequestBody CompletePaymentCheckoutRequest request) {
        return ResponseEntity.ok(abonnementService.completeRenewalCheckout(id, request));
    }

    /**
     * Migrer vers un plan d'abonnement supérieur (Upgrade).
     */
    @PutMapping("/{id}/upgrade")
    public ResponseEntity<AbonnementResponse> upgrader(
            @PathVariable Long id,
            @RequestParam Long nouveauPlanId,
            @RequestParam Long paiementId) {
        return ResponseEntity.ok(abonnementService.upgrader(id, nouveauPlanId, paiementId));
    }

    @PostMapping("/{id}/upgrade-checkout/prepare")
    public ResponseEntity<CheckoutPreparationResponse> prepareUpgradeCheckout(
            @PathVariable Long id,
            @Valid @RequestBody UpgradeCheckoutRequest request) {
        return ResponseEntity.ok(abonnementService.prepareUpgradeCheckout(id, request));
    }

    @PostMapping("/{id}/upgrade-checkout/complete")
    public ResponseEntity<AbonnementResponse> completeUpgradeCheckout(
            @PathVariable Long id,
            @Valid @RequestBody CompletePaymentCheckoutRequest request) {
        return ResponseEntity.ok(abonnementService.completeUpgradeCheckout(id, request));
    }

    /**
     * Obtenir le nombre de jours restants avant l'expiration de l'abonnement.
     */
    @GetMapping("/{id}/duree-restante")
    public ResponseEntity<Integer> getDureeRestante(@PathVariable Long id) {
        return ResponseEntity.ok(abonnementService.getDureeRestante(id));
    }

    /**
     * Vérifier si l'abonnement autorise encore des commandes ce mois-ci.
     */
    @GetMapping("/{id}/verifier-limite")
    public ResponseEntity<Boolean> verifierLimiteCommandes(
            @PathVariable Long id,
            @RequestParam int commandesEffectuees) {
        boolean estAutorise = abonnementService.verifierLimiteCommandesMois(id, commandesEffectuees);
        return ResponseEntity.ok(estAutorise);
    }

    /**
     * Forcer manuellement la vérification de l'expiration d'un abonnement.
     */
    @PostMapping("/{id}/verifier-expiration")
    public ResponseEntity<Boolean> verifierExpiration(@PathVariable Long id) {
        boolean aExpire = abonnementService.verifierExpiration(id);
        return ResponseEntity.ok(aExpire);
    }
}
