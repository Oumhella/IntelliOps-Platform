package org.example.paiment_service.repository;

import org.example.paiment_service.entity.TransactionPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionPaiementRepository extends JpaRepository<TransactionPaiement, Long> {
    Optional<TransactionPaiement> findByIdempotencyKey(String idempotencyKey);}