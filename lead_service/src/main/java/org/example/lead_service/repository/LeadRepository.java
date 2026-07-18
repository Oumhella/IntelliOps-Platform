package org.example.lead_service.repository;

import org.example.lead_service.entity.Lead;
import org.example.lead_service.entity.StatutLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    List<Lead> findByAgentId(Long agentId);
    List<Lead> findByStatutLead(StatutLead statutLead);
}
