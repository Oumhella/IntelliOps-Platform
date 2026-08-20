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
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Collection;

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
              and (:livreurId is null or delivery.livreurId = :livreurId)
            """)
    Page<Livraison> search(
            @Param("enterpriseId") Long enterpriseId,
            @Param("statut") StatutLivraison statut,
            @Param("transporteur") TypeTransporteur transporteur,
            @Param("livreurId") Long livreurId,
            Pageable pageable);

    long countByEnterpriseIdAndLivreurIdAndShippingDateGreaterThanEqual(
            Long enterpriseId, Long livreurId, LocalDateTime since);

    long countByEnterpriseIdAndLivreurIdAndDeliveryDateGreaterThanEqual(
            Long enterpriseId, Long livreurId, LocalDateTime since);

    long countByEnterpriseIdAndLivreurIdAndStatutLivraison(
            Long enterpriseId, Long livreurId, StatutLivraison status);

    long countByEnterpriseIdAndLivreurIdAndStatutLivraisonIn(
            Long enterpriseId, Long livreurId, Collection<StatutLivraison> statuses);

    @Query("""
            select coalesce(sum(delivery.codCollectedAmount), 0) from Livraison delivery
            where delivery.enterpriseId = :enterpriseId
              and delivery.livreurId = :livreurId
              and delivery.statutLivraison = org.example.delivery_service.entity.StatutLivraison.LIVREE
              and delivery.codCollectedAmount > 0
              and delivery.codReconciledAt is null
            """)
    BigDecimal sumUnreconciledCod(
            @Param("enterpriseId") Long enterpriseId,
            @Param("livreurId") Long livreurId);
}
