package org.example.paiment_service.repository;

import org.example.paiment_service.entity.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface FactureRepository extends JpaRepository<Facture, Long> {
    Optional<Facture> findByNumeroFactureUnique(String numeroFactureUnique);
    Optional<Facture> findByIdAndTransactionPaiementEnterpriseId(Long id, Long enterpriseId);
    Page<Facture> findAllByTransactionPaiementEnterpriseId(Long enterpriseId, Pageable pageable);
}
