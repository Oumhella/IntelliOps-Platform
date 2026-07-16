package org.example.abonnement_service.repository;

import org.example.abonnement_service.entity.Abonnement;
import org.example.abonnement_service.entity.StatutAbonnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    List<Abonnement> findByUserId(Long userId);

    Optional<Abonnement> findByUserIdAndStatut(Long userId, StatutAbonnement statut);

    boolean existsByUserIdAndStatut(Long userId, StatutAbonnement statut);
}
