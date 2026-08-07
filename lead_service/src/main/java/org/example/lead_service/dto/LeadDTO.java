package org.example.lead_service.dto;

import lombok.Data;
import org.example.lead_service.entity.OrdrePriorite;
import org.example.lead_service.entity.StatutLead;
import org.example.lead_service.entity.LeadSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Data
public class LeadDTO {
    private Long idLead;
    private StatutLead statutLead;
    @NotNull
    private OrdrePriorite ordrePriorite;
    @Valid
    @NotNull
    private CoordonneesClientDTO infosClient;
    private Long boutiqueId;
    private Long agentId;
    private LeadSource source;
}
