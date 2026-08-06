package org.example.paiment_service.repository;

import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.StatutPaiement;
import org.example.paiment_service.entity.TransactionPaiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionPaiementRepository extends JpaRepository<TransactionPaiement, Long> {
    Optional<TransactionPaiement> findByIdempotencyKeyAndEnterpriseId(String idempotencyKey, Long enterpriseId);
    Optional<TransactionPaiement> findByIdAndEnterpriseId(Long id, Long enterpriseId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from TransactionPaiement payment where payment.id = :id and payment.enterpriseId = :enterpriseId")
    Optional<TransactionPaiement> findForFinalization(
            @Param("id") Long id,
            @Param("enterpriseId") Long enterpriseId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from TransactionPaiement payment where payment.id = :id and payment.enterpriseId = :enterpriseId")
    Optional<TransactionPaiement> findForConsumption(
            @Param("id") Long id,
            @Param("enterpriseId") Long enterpriseId);

    @Query("""
            select payment from TransactionPaiement payment
            where payment.enterpriseId = :enterpriseId
              and (:statut is null or payment.statut = :statut)
              and (:contexte is null or payment.typeContexte = :contexte)
            """)
    Page<TransactionPaiement> search(
            @Param("enterpriseId") Long enterpriseId,
            @Param("statut") StatutPaiement statut,
            @Param("contexte") Contexte contexte,
            Pageable pageable);
}
