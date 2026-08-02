package org.example.lead_service.repository;

import org.example.lead_service.entity.Lead;
import org.example.lead_service.entity.StatutLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    List<Lead> findByAgentIdAndEnterpriseId(Long agentId, Long enterpriseId);
    Optional<Lead> findByIdLeadAndEnterpriseId(Long idLead, Long enterpriseId);

    @Query("""
            select lead from Lead lead
            where lead.enterpriseId = :enterpriseId
              and (:statut is null or lead.statutLead = :statut)
              and (:agentId is null or lead.agentId = :agentId)
            """)
    Page<Lead> search(
            @Param("enterpriseId") Long enterpriseId,
            @Param("statut") StatutLead statut,
            @Param("agentId") Long agentId,
            Pageable pageable);
}
