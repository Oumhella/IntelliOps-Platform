package org.example.abonnement_service.repository;

import org.example.abonnement_service.entity.ChangementPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChangementPlanRepository extends JpaRepository<ChangementPlan, Long> {
    Optional<ChangementPlan> findByPaiementId(Long paiementId);
}
