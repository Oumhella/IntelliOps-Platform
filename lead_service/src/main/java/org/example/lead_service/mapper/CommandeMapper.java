package org.example.lead_service.mapper;

import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.LignesCommandeDTO;
import org.example.lead_service.entity.Commande;
import org.example.lead_service.entity.LignesCommande;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {LeadMapper.class})
public interface CommandeMapper {

    @Mapping(target = "lignesCommande", source = "lignesCommande")
    CommandeDTO toDto(Commande commande);

    @Mapping(target = "lignesCommande", source = "lignesCommande")
    Commande toEntity(CommandeDTO commandeDTO);

    LignesCommandeDTO toDto(LignesCommande lignesCommande);
    LignesCommande toEntity(LignesCommandeDTO lignesCommandeDTO);
}