package org.example.abonnement_service.mapper;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.example.abonnement_service.dto.response.PlanAbonnementResponse;
import org.example.abonnement_service.entity.Abonnement;
import org.example.abonnement_service.entity.PlanAbonnement;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AbonnementMapper {
    // Mappings individuels
    AbonnementResponse toResponse(Abonnement abonnement);
    PlanAbonnementResponse toPlanResponse(PlanAbonnement planAbonnement);
    PlanAbonnement toEntity(org.example.abonnement_service.dto.request.PlanAbonnementRequest request);

    // Mappings de listes
    List<AbonnementResponse> toResponseList(List<Abonnement> abonnements);
    List<PlanAbonnementResponse> toPlanResponseList(List<PlanAbonnement> plans);
}
