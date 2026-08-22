package org.example.lead_service.repository;

import org.example.lead_service.entity.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.example.lead_service.entity.StatutCommande;

import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface CommandeRepository extends JpaRepository<Commande, Long> {

    Optional<Commande> findByReference(String reference);
    Optional<Commande> findByReferenceAndLeadEnterpriseId(String reference, Long enterpriseId);
    Optional<Commande> findByIdCommandeAndLeadEnterpriseId(Long idCommande, Long enterpriseId);
    long countByLeadEnterpriseIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long enterpriseId, LocalDateTime from, LocalDateTime until);

    @Query("""
            select commande from Commande commande
            where commande.lead.enterpriseId = :enterpriseId
              and (:statut is null or commande.statutCommande = :statut)
              and (:agentId is null or commande.lead.agentId = :agentId)
            """)
    Page<Commande> search(
            @Param("enterpriseId") Long enterpriseId,
            @Param("statut") StatutCommande statut,
            @Param("agentId") Long agentId,
            Pageable pageable);
}
