package org.example.abonnement_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.abonnement_service.dto.request.PlanAbonnementRequest;
import org.example.abonnement_service.dto.response.PlanAbonnementResponse;
import org.example.abonnement_service.entity.PlanAbonnement;
import org.example.abonnement_service.entity.StatutOffre;
import org.example.abonnement_service.mapper.AbonnementMapper;
import org.example.abonnement_service.repository.PlanAbonnementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanAbonnementController {

    private final org.example.abonnement_service.service.PlanAbonnementService planAbonnementService;

    /**
     * Créer un nouveau plan d'abonnement.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanAbonnementResponse> creerPlan(@Valid @RequestBody PlanAbonnementRequest request) {
        PlanAbonnementResponse response = planAbonnementService.creerPlan(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Récupérer tous les plans d'abonnement.
     * Possibilité de filtrer par statut actif.
     */
    @GetMapping
    public ResponseEntity<List<PlanAbonnementResponse>> getTousLesPlans(
            @RequestParam(required = false) StatutOffre statut) {
        return ResponseEntity.ok(planAbonnementService.getTousLesPlans(statut));
    }

    /**
     * Récupérer un plan d'abonnement par son ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlanAbonnementResponse> getPlanById(@PathVariable Long id) {
        return ResponseEntity.ok(planAbonnementService.getPlanById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanAbonnementResponse> modifierPlan(
            @PathVariable Long id,
            @Valid @RequestBody PlanAbonnementRequest request) {
        return ResponseEntity.ok(planAbonnementService.modifierPlan(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimerPlan(@PathVariable Long id) {
        planAbonnementService.supprimerPlan(id);
        return ResponseEntity.noContent().build();
    }
}
