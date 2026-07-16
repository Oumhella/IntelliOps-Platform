package org.example.abonnement_service.repository;

import org.example.abonnement_service.entity.Desactivation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DesactivationRepository extends JpaRepository<Desactivation, Long> {
    // Récupérer toutes les périodes de pause pour un abonnement donné
    List<Desactivation> findByAbonnementIdAbonnement(Long idAbonnement);
}
