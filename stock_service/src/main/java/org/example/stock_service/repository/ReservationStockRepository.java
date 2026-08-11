package org.example.stock_service.repository;

import org.example.stock_service.entity.ReservationStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationStockRepository extends JpaRepository<ReservationStock, Long> {
    Optional<ReservationStock> findByEnterpriseIdAndReferenceOperationAndProduitId(
            Long enterpriseId, String referenceOperation, Long produitId);
}
