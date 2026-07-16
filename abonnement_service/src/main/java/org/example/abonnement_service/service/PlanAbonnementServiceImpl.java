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
        PlanAbonnement plan = planAbonnementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan d'abonnement introuvable avec l'ID : " + id));
        return abonnementMapper.toPlanResponse(plan);
    }
}
