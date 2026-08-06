package org.example.abonnement_service.service;

import org.example.abonnement_service.dto.request.AbonnementRequest;
import org.example.abonnement_service.dto.request.PaymentCheckoutRequest;
import org.example.abonnement_service.dto.request.SubscriptionCheckoutRequest;
import org.example.abonnement_service.dto.request.UpgradeCheckoutRequest;
import org.example.abonnement_service.dto.request.CompletePaymentCheckoutRequest;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.example.abonnement_service.dto.response.CheckoutPreparationResponse;
import org.example.abonnement_service.entity.Abonnement;

import java.util.List;
import org.example.common.dto.PageResponse;
import org.example.abonnement_service.entity.StatutAbonnement;

public interface AbonnementService {

    AbonnementResponse souscrire(AbonnementRequest request);
    CheckoutPreparationResponse prepareCheckout(SubscriptionCheckoutRequest request);
    AbonnementResponse completeCheckout(CompletePaymentCheckoutRequest request);
    void suspendre(Long idAbonnement, String motif);
    boolean verifierExpiration(Long idAbonnement);
    AbonnementResponse renouveler(Long idAbonnement, Long paiementId);
    CheckoutPreparationResponse prepareRenewalCheckout(Long idAbonnement, PaymentCheckoutRequest request);
    AbonnementResponse completeRenewalCheckout(Long idAbonnement, CompletePaymentCheckoutRequest request);
    AbonnementResponse upgrader(Long idAbonnement, Long nouveauPlanId, Long paiementId);
    CheckoutPreparationResponse prepareUpgradeCheckout(Long idAbonnement, UpgradeCheckoutRequest request);
    AbonnementResponse completeUpgradeCheckout(Long idAbonnement, CompletePaymentCheckoutRequest request);
    boolean verifierLimiteCommandesMois(Long idAbonnement, int commandesEffectuees);
    int getDureeRestante(Long idAbonnement);

    AbonnementResponse getAbonnementById(Long idAbonnement);
    List<AbonnementResponse> getHistoriqueUtilisateur(Long userId);
    PageResponse<AbonnementResponse> search(StatutAbonnement statut, int page, int size);
}
