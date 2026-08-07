package org.example.abonnement_service.controller;

import lombok.RequiredArgsConstructor;
import org.example.abonnement_service.dto.response.EntitlementResponse;
import org.example.abonnement_service.entity.StatutAbonnement;
import org.example.abonnement_service.repository.AbonnementRepository;
import org.example.common.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/abonnements/entitlement")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class EntitlementController {

    private final AbonnementRepository abonnementRepository;

    @GetMapping
    public EntitlementResponse currentEntitlement() {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        var subscription = abonnementRepository
                .findFirstByEnterpriseIdAndStatutOrderByDateFinDesc(enterpriseId, StatutAbonnement.ACTIF)
                .orElse(null);

        if (subscription == null) {
            return new EntitlementResponse(false, null, null, 0, null,
                    "No active subscription exists for this enterprise.");
        }

        LocalDate today = LocalDate.now();
        if (subscription.getDateDebut().isAfter(today) || subscription.getDateFin().isBefore(today)) {
            return new EntitlementResponse(false, subscription.getIdAbonnement(),
                    subscription.getPlanAbonnement().getNomPlan(),
                    subscription.getPlanAbonnement().getLimiteCommandesMois(),
                    subscription.getDateFin(), "The subscription is outside its validity period.");
        }

        return new EntitlementResponse(true, subscription.getIdAbonnement(),
                subscription.getPlanAbonnement().getNomPlan(),
                subscription.getPlanAbonnement().getLimiteCommandesMois(),
                subscription.getDateFin(), null);
    }
}
