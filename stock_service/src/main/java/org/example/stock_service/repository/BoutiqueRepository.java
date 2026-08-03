package org.example.stock_service.repository;

import org.example.stock_service.entity.Boutique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoutiqueRepository extends JpaRepository<Boutique, Long> {
    Optional<Boutique> findByIdBoutiqueAndEnterpriseId(Long idBoutique, Long enterpriseId);
    List<Boutique> findAllByEnterpriseIdOrderByNomBoutiqueAsc(Long enterpriseId);
}
