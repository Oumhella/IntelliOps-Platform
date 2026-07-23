package org.example.delivery_service.mapper;

import org.example.delivery_service.dto.response.LivraisonResponse;
import org.example.delivery_service.entity.Livraison;
import org.springframework.stereotype.Component;

@Component
public class LivraisonMapper {

    public LivraisonResponse toResponse(Livraison entity) {
        if (entity == null) return null;

        return LivraisonResponse.builder()
                .idLivraison(entity.getIdLivraison())
                .referenceCommandeId(entity.getReferenceCommandeId())
                .codeSuiviTracking(entity.getCodeSuiviTracking())
                .statutLivraison(entity.getStatutLivraison())
                .typeTransporteur(entity.getTypeTransporteur())
                .nomSociete(entity.getNomSociete())
                .externalLivreurId(entity.getExternalLivreurId())
                .shippingDate(entity.getShippingDate())
                .deliveryDate(entity.getDeliveryDate())
                .montantACollecterCoD(entity.getMontantACollecterCoD())
                .delaiJours(entity.calculerDelaiJours())
                .build();
    }
}
