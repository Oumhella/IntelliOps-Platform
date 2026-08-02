package org.example.abonnement_service.repository;

import org.example.abonnement_service.entity.Abonnement;
import org.example.abonnement_service.entity.StatutAbonnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    List<Abonnement> findByUserIdAndEnterpriseId(Long userId, Long enterpriseId);

    Optional<Abonnement> findByIdAbonnementAndEnterpriseId(Long idAbonnement, Long enterpriseId);

    boolean existsByUserIdAndStatutAndEnterpriseId(Long userId, StatutAbonnement statut, Long enterpriseId);

    Page<Abonnement> findAllByEnterpriseIdAndStatut(
            Long enterpriseId, StatutAbonnement statut, Pageable pageable);

    Page<Abonnement> findAllByEnterpriseId(Long enterpriseId, Pageable pageable);
}
