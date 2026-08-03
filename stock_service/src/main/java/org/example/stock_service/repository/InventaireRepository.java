package org.example.stock_service.repository;

import org.example.stock_service.entity.Inventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface InventaireRepository extends JpaRepository<Inventaire, Long> {
    Optional<Inventaire> findByBoutiqueIdBoutiqueAndProduitIdProduitAndBoutiqueEnterpriseId(
            Long idBoutique, Long idProduit, Long enterpriseId);
    Optional<Inventaire> findByIdAndBoutiqueEnterpriseId(Long id, Long enterpriseId);
    List<Inventaire> findByBoutiqueIdBoutiqueAndBoutiqueEnterpriseId(Long idBoutique, Long enterpriseId);
    boolean existsByProduitIdProduit(Long idProduit);
}
