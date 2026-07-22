package org.example.paiment_service.mapper;

import org.example.paiment_service.dto.request.*;
import org.example.paiment_service.dto.response.*;
import org.example.paiment_service.entity.*;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {

    FactureResponseDTO toResponse(Facture facture);

    @Mapping(target = "facture", source = "facture")
    TransactionPaiementResponseDTO toResponse(TransactionPaiement transaction);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "facture", ignore = true)
    @Mapping(target = "tokenisation", ignore = true)
    TransactionPaiement toEntity(InitierPaiementRequestDTO dto);
}