package org.example.delivery_service.repository;

import org.example.delivery_service.entity.Livraison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LivraisonRepository extends JpaRepository<Livraison, Long> {

    Optional<Livraison> findByCodeSuiviTracking(String codeSuiviTracking);

    Optional<Livraison> findByReferenceCommandeId(Long referenceCommandeId);

    boolean existsByReferenceCommandeId(Long referenceCommandeId);
}