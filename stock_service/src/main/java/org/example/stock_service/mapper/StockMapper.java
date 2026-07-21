package org.example.stock_service.mapper;

import org.example.stock_service.dto.request.*;
import org.example.stock_service.dto.response.*;
import org.example.stock_service.entity.*;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface StockMapper {

    // --- Boutique Mappings ---
    BoutiqueResponseDTO toResponse(Boutique boutique);
    Boutique toEntity(BoutiqueRequestDTO dto);

    // --- Produit Mappings ---
    ProduitResponseDTO toResponse(Produit produit);
    Produit toEntity(ProduitRequestDTO dto);

    // --- RegleApprovisionnement Mappings ---
    RegleApprovisionnementResponseDTO toResponse(RegleApprovisionnement regle);
    RegleApprovisionnement toEntity(RegleApprovisionnementRequestDTO dto);

    // --- MouvementStock Mappings ---
    MouvementStockResponseDTO toResponse(MouvementStock mouvementStock);

    // --- Inventaire Mappings ---
    @Mapping(target = "boutique", source = "boutique")
    @Mapping(target = "produit", source = "produit")
    @Mapping(target = "regleApprovisionnement", source = "regleApprovisionnement")
    @Mapping(target = "mouvements", source = "mouvements")
    InventaireResponseDTO toResponse(Inventaire inventaire);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "quantiteReservee", constant = "0")
    @Mapping(target = "mouvements", ignore = true)
    @Mapping(target = "boutique", ignore = true)
    @Mapping(target = "produit", ignore = true)
    Inventaire toEntity(InventaireResponseDTO dto);
}