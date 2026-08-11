package org.example.abonnement_service.repository;

import org.example.abonnement_service.entity.Abonnement;
import org.example.abonnement_service.entity.StatutAbonnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    List<Abonnement> findByUserIdAndEnterpriseId(Long userId, Long enterpriseId);

    Optional<Abonnement> findByIdAbonnementAndEnterpriseId(Long idAbonnement, Long enterpriseId);

    Optional<Abonnement> findByPaiementIdAndEnterpriseId(Long paiementId, Long enterpriseId);

    boolean existsByEnterpriseIdAndStatutIn(Long enterpriseId, Collection<StatutAbonnement> statuts);

    Optional<Abonnement> findFirstByEnterpriseIdAndStatutOrderByDateFinDesc(
            Long enterpriseId, StatutAbonnement statut);

    Page<Abonnement> findAllByEnterpriseIdAndStatut(
            Long enterpriseId, StatutAbonnement statut, Pageable pageable);

    Page<Abonnement> findAllByEnterpriseId(Long enterpriseId, Pageable pageable);
}
