package org.example.lead_service.dto;

import lombok.Data;
import org.example.lead_service.entity.OrdrePriorite;
import org.example.lead_service.entity.StatutLead;

@Data
public class LeadDTO {
    private Long idLead;
    private StatutLead statutLead;
    private OrdrePriorite ordrePriorite;
    private CoordonneesClientDTO infosClient;
    private Long boutiqueId;
    private Long agentId;
}