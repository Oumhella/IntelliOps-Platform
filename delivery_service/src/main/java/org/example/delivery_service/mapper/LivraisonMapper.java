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
                .livreurId(entity.getLivreurId())
                .shippingDate(entity.getShippingDate())
                .deliveryDate(entity.getDeliveryDate())
                .montantACollecterCoD(entity.getMontantACollecterCoD())
                .delaiJours(entity.calculerDelaiJours())
                .clientNomComplet(entity.getClientNomComplet())
                .clientEmail(entity.getClientEmail())
                .clientTelephone(entity.getClientTelephone())
                .adresseLivraison(entity.getAdresseLivraison())
                .villeLivraison(entity.getVilleLivraison())
                .acceptedAt(entity.getAcceptedAt())
                .startedAt(entity.getStartedAt())
                .lastAttemptAt(entity.getLastAttemptAt())
                .returnRequestedAt(entity.getReturnRequestedAt())
                .attemptCount(entity.getAttemptCount())
                .failureReason(entity.getFailureReason())
                .failureNote(entity.getFailureNote())
                .lastLatitude(entity.getLastLatitude())
                .lastLongitude(entity.getLastLongitude())
                .deliveredTo(entity.getDeliveredTo())
                .proofSignature(entity.getProofSignature())
                .proofPhotoAvailable(entity.getProofPhotoObjectKey() != null)
                .proofCapturedAt(entity.getProofCapturedAt())
                .codCollectedAmount(entity.getCodCollectedAmount())
                .codDiscrepancyNote(entity.getCodDiscrepancyNote())
                .codReconciledAt(entity.getCodReconciledAt())
                .build();
    }
}
