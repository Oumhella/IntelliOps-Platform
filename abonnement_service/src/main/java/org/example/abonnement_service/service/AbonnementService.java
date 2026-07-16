package org.example.abonnement_service.service;

import org.example.abonnement_service.dto.request.AbonnementRequest;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.example.abonnement_service.entity.Abonnement;

import java.util.List;

public interface AbonnementService {

    AbonnementResponse souscrire(AbonnementRequest request);
    void suspendre(Long idAbonnement, String motif);
    boolean verifierExpiration(Long idAbonnement);
    void renouveler(Long idAbonnement, Long paiementId);
    void upgrader(Long idAbonnement, Long nouveauPlanId);
    boolean verifierLimiteCommandesMois(Long idAbonnement, int commandesEffectuees);
    int getDureeRestante(Long idAbonnement);

    AbonnementResponse getAbonnementById(Long idAbonnement);
    List<AbonnementResponse> getHistoriqueUtilisateur(Long userId);
}
