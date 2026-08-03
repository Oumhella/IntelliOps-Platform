package org.example.delivery_service.repository;

import org.example.delivery_service.entity.Livraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;

@Repository
public interface LivraisonRepository extends JpaRepository<Livraison, Long> {

    Optional<Livraison> findByCodeSuiviTrackingAndEnterpriseId(String codeSuiviTracking, Long enterpriseId);

    Optional<Livraison> findByReferenceCommandeIdAndEnterpriseId(Long referenceCommandeId, Long enterpriseId);

    Optional<Livraison> findByIdLivraisonAndEnterpriseId(Long idLivraison, Long enterpriseId);

    boolean existsByReferenceCommandeIdAndEnterpriseId(Long referenceCommandeId, Long enterpriseId);

    @Query("""
            select delivery from Livraison delivery
            where delivery.enterpriseId = :enterpriseId
              and (:statut is null or delivery.statutLivraison = :statut)
              and (:transporteur is null or delivery.typeTransporteur = :transporteur)
            """)
    Page<Livraison> search(
            @Param("enterpriseId") Long enterpriseId,
            @Param("statut") StatutLivraison statut,
            @Param("transporteur") TypeTransporteur transporteur,
            Pageable pageable);
}
