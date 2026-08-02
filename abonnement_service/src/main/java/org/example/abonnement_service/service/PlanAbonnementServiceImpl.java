package org.example.abonnement_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.abonnement_service.dto.request.PlanAbonnementRequest;
import org.example.abonnement_service.dto.response.PlanAbonnementResponse;
import org.example.abonnement_service.entity.PlanAbonnement;
import org.example.abonnement_service.entity.StatutOffre;
import org.example.abonnement_service.mapper.AbonnementMapper;
import org.example.abonnement_service.repository.PlanAbonnementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanAbonnementServiceImpl implements PlanAbonnementService {

    private final PlanAbonnementRepository planAbonnementRepository;
    private final AbonnementMapper abonnementMapper;

    @Override
    @Transactional
    public PlanAbonnementResponse creerPlan(PlanAbonnementRequest request) {
        PlanAbonnement plan = abonnementMapper.toEntity(request);
        PlanAbonnement savedPlan = planAbonnementRepository.save(plan);
        return abonnementMapper.toPlanResponse(savedPlan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanAbonnementResponse> getTousLesPlans(StatutOffre statut) {
        List<PlanAbonnement> plans;
        if (statut != null) {
            plans = planAbonnementRepository.findByEstActif(statut);
        } else {
            plans = planAbonnementRepository.findAll();
        }
        return abonnementMapper.toPlanResponseList(plans);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanAbonnementResponse getPlanById(Long id) {
        return abonnementMapper.toPlanResponse(findPlan(id));
    }

    @Override
    @Transactional
    public PlanAbonnementResponse modifierPlan(Long id, PlanAbonnementRequest request) {
        PlanAbonnement plan = findPlan(id);
        plan.setNomPlan(request.getNomPlan());
        plan.setDescription(request.getDescription());
        plan.setPrix(request.getPrix());
        plan.setDuree(request.getDuree());
        plan.setMinJoursEntreDesactivation(request.getMinJoursEntreDesactivation());
        plan.setMaxPeriodeDesactivation(request.getMaxPeriodeDesactivation());
        plan.setEstActif(request.getEstActif());
        plan.setLimiteCommandesMois(request.getLimiteCommandesMois());
        return abonnementMapper.toPlanResponse(planAbonnementRepository.save(plan));
    }

    @Override
    @Transactional
    public void supprimerPlan(Long id) {
        PlanAbonnement plan = findPlan(id);
        plan.setEstActif(StatutOffre.SUPPRIME);
        planAbonnementRepository.save(plan);
    }

    private PlanAbonnement findPlan(Long id) {
        return planAbonnementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Plan d'abonnement introuvable avec l'ID : " + id));
    }
}
