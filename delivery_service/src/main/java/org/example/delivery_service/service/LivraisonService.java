package org.example.delivery_service.service;

import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.UpdateStatutRequest;
import org.example.delivery_service.dto.request.AssignCourierRequest;
import org.example.delivery_service.dto.response.LivraisonResponse;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
import org.example.delivery_service.dto.request.CompleteDeliveryRequest;
import org.example.delivery_service.dto.request.FailedDeliveryAttemptRequest;
import org.example.delivery_service.dto.response.CourierDashboardResponse;
import org.example.delivery_service.dto.response.ProofPhotoResponse;
import org.springframework.web.multipart.MultipartFile;
import org.example.common.dto.PageResponse;

public interface LivraisonService {
    LivraisonResponse expedierLivraison(ExpedierLivraisonRequest request);
    LivraisonResponse getById(Long id);
    PageResponse<LivraisonResponse> search(StatutLivraison statut, TypeTransporteur transporteur, int page, int size);
    LivraisonResponse getByTrackingNumber(String trackingNum);
    LivraisonResponse getByCommandeId(Long commandeId);
    LivraisonResponse mettreAJourStatut(Long id, UpdateStatutRequest request);
    LivraisonResponse confirmerReception(Long id);
    LivraisonResponse assignerLivreur(Long id, AssignCourierRequest request);
    LivraisonResponse acceptAssignment(Long id);
    LivraisonResponse startDelivery(Long id);
    LivraisonResponse reportFailedAttempt(Long id, FailedDeliveryAttemptRequest request);
    LivraisonResponse requestReturn(Long id);
    LivraisonResponse completeDelivery(Long id, CompleteDeliveryRequest request, MultipartFile proofPhoto);
    LivraisonResponse reconcileCod(Long id);
    CourierDashboardResponse courierDashboard();
    ProofPhotoResponse getProofPhoto(Long id);
}
