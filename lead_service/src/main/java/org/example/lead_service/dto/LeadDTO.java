package org.example.lead_service.dto;

import lombok.Data;
import org.example.lead_service.entity.OrdrePriorite;
import org.example.lead_service.entity.StatutLead;
import org.example.lead_service.entity.LeadSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

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
    /** Present when a sales-channel import already created the linked order. */
    private Long commandeId;
    private String commandeReference;
    private Long stockLocationId;
    private Boolean hasPrebuiltOrder;
    private List<LignesCommandeDTO> lignesCommande;
}
