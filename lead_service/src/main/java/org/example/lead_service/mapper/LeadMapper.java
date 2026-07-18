package org.example.lead_service.mapper;

import org.example.lead_service.dto.CoordonneesClientDTO;
import org.example.lead_service.dto.LeadDTO;
import org.example.lead_service.dto.NoteInteractionDTO;
import org.example.lead_service.entity.CoordonneesClient;
import org.example.lead_service.entity.Lead;
import org.example.lead_service.entity.NoteInteraction;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LeadMapper {

    LeadDTO toDto(Lead lead);
    Lead toEntity(LeadDTO leadDTO);

    CoordonneesClientDTO toDto(CoordonneesClient coordonneesClient);
    CoordonneesClient toEntity(CoordonneesClientDTO coordonneesClientDTO);

    NoteInteractionDTO toDto(NoteInteraction noteInteraction);
    NoteInteraction toEntity(NoteInteractionDTO noteInteractionDTO);
}