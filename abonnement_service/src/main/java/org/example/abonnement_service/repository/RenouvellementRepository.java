package org.example.abonnement_service.repository;

import org.example.abonnement_service.entity.Renouvellement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RenouvellementRepository extends JpaRepository<Renouvellement, Long> {
    // Récupérer tout l'historique des renouvellements d'un abonnement spécifique
    List<Renouvellement> findByAbonnementIdAbonnementOrderByDateRenouvellementDesc(Long idAbonnement);
    Optional<Renouvellement> findByPaiementId(Long paiementId);
}
