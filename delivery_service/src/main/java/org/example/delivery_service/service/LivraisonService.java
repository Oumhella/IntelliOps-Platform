package org.example.delivery_service.service;

import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.UpdateStatutRequest;
import org.example.delivery_service.dto.response.LivraisonResponse;

public interface LivraisonService {
    LivraisonResponse expedierLivraison(ExpedierLivraisonRequest request);
    LivraisonResponse getByTrackingNumber(String trackingNum);
    LivraisonResponse getByCommandeId(Long commandeId);
    LivraisonResponse mettreAJourStatut(Long id, UpdateStatutRequest request);
    LivraisonResponse confirmerReception(Long id);
}