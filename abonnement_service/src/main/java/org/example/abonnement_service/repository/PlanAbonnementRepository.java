package org.example.abonnement_service.repository;

import org.example.abonnement_service.entity.PlanAbonnement;
import org.example.abonnement_service.entity.StatutOffre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanAbonnementRepository extends JpaRepository<PlanAbonnement, Long> {

    List<PlanAbonnement> findByEstActif(StatutOffre estActif);
}
