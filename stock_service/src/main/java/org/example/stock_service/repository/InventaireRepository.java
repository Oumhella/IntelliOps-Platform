package org.example.stock_service.repository;

import org.example.stock_service.entity.Inventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface InventaireRepository extends JpaRepository<Inventaire, Long> {
    Optional<Inventaire> findByBoutiqueIdBoutiqueAndProduitIdProduit(Long idBoutique, Long idProduit);
    List<Inventaire> findByBoutiqueIdBoutique(Long idBoutique);
}