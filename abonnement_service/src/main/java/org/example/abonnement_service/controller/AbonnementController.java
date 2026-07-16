package org.example.abonnement_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.abonnement_service.dto.request.AbonnementRequest;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.abonnement_service.service.AbonnementService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/abonnements")
@RequiredArgsConstructor
public class AbonnementController {

    private final AbonnementService abonnementService;

    /**
     * Souscrire à un nouvel abonnement.
     */
    @PostMapping
    public ResponseEntity<AbonnementResponse> souscrire(@Valid @RequestBody AbonnementRequest request) {
        AbonnementResponse response = abonnementService.souscrire(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
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
    public ResponseEntity<Void> renouveler(
            @PathVariable Long id,
            @RequestParam Long paiementId) {
        abonnementService.renouveler(id, paiementId);
        return ResponseEntity.ok().build();
    }

    /**
     * Migrer vers un plan d'abonnement supérieur (Upgrade).
     */
    @PutMapping("/{id}/upgrade")
    public ResponseEntity<Void> upgrader(
            @PathVariable Long id,
            @RequestParam Long nouveauPlanId) {
        abonnementService.upgrader(id, nouveauPlanId);
        return ResponseEntity.ok().build();
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
