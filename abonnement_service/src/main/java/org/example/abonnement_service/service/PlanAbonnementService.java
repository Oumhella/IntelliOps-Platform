package org.example.abonnement_service.service;

import org.example.abonnement_service.dto.request.PlanAbonnementRequest;
import org.example.abonnement_service.dto.response.PlanAbonnementResponse;
import org.example.abonnement_service.entity.StatutOffre;

import java.util.List;

public interface PlanAbonnementService {

    PlanAbonnementResponse creerPlan(PlanAbonnementRequest request);

    List<PlanAbonnementResponse> getTousLesPlans(StatutOffre statut);

    PlanAbonnementResponse getPlanById(Long id);
}
