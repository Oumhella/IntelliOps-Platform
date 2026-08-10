package org.example.lead_service.mapper;

import org.example.lead_service.dto.CoordonneesClientDTO;
import org.example.lead_service.dto.LeadDTO;
import org.example.lead_service.dto.LignesCommandeDTO;
import org.example.lead_service.dto.NoteInteractionDTO;
import org.example.lead_service.entity.Commande;
import org.example.lead_service.entity.CoordonneesClient;
import org.example.lead_service.entity.Lead;
import org.example.lead_service.entity.LignesCommande;
import org.example.lead_service.entity.NoteInteraction;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LeadMapper {

    @Mapping(target = "commandeId", ignore = true)
    @Mapping(target = "commandeReference", ignore = true)
    @Mapping(target = "stockLocationId", ignore = true)
    @Mapping(target = "hasPrebuiltOrder", ignore = true)
    @Mapping(target = "lignesCommande", ignore = true)
    LeadDTO toDto(Lead lead);

    @Mapping(target = "commande", ignore = true)
    @Mapping(target = "historiqueInteractions", ignore = true)
    @Mapping(target = "enterpriseId", ignore = true)
    Lead toEntity(LeadDTO leadDTO);

    CoordonneesClientDTO toDto(CoordonneesClient coordonneesClient);
    CoordonneesClient toEntity(CoordonneesClientDTO coordonneesClientDTO);

    NoteInteractionDTO toDto(NoteInteraction noteInteraction);
    NoteInteraction toEntity(NoteInteractionDTO noteInteractionDTO);

    @AfterMapping
    default void mapPrebuiltOrder(Lead lead, @MappingTarget LeadDTO dto) {
        Commande order = lead.getCommande();
        if (order == null) {
            dto.setHasPrebuiltOrder(false);
            return;
        }
        List<LignesCommande> lines = order.getLignesCommande();
        boolean prebuilt = lines != null && !lines.isEmpty();
        dto.setCommandeId(order.getIdCommande());
        dto.setCommandeReference(order.getReference());
        dto.setStockLocationId(order.getStockLocationId() != null ? order.getStockLocationId() : lead.getBoutiqueId());
        dto.setHasPrebuiltOrder(prebuilt);
        if (prebuilt) {
            dto.setLignesCommande(lines.stream().map(LeadMapper::toLineDto).toList());
        }
    }

    private static LignesCommandeDTO toLineDto(LignesCommande line) {
        LignesCommandeDTO dto = new LignesCommandeDTO();
        dto.setIdLigne(line.getIdLigne());
        dto.setProduitId(line.getProduitId());
        dto.setQuantite(line.getQuantite());
        dto.setPrixUnitaireApplique(line.getPrixUnitaireApplique());
        return dto;
    }
}
