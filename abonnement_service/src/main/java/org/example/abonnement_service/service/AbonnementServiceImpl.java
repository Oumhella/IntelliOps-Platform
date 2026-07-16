package org.example.abonnement_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.abonnement_service.dto.request.AbonnementRequest;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.example.abonnement_service.entity.*;
import org.example.abonnement_service.mapper.AbonnementMapper;
import org.example.abonnement_service.repository.AbonnementRepository;
import org.example.abonnement_service.repository.DesactivationRepository;
import org.example.abonnement_service.repository.PlanAbonnementRepository;
import org.example.abonnement_service.repository.RenouvellementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.example.abonnement_service.entity.DureeOffre.ANNUEL;

@Service
@RequiredArgsConstructor
public class AbonnementServiceImpl implements AbonnementService {

    private final AbonnementRepository abonnementRepository;
    private final PlanAbonnementRepository planAbonnementRepository;
    private final RenouvellementRepository renouvellementRepository;
    private final DesactivationRepository desactivationRepository;
    private final AbonnementMapper abonnementMapper;
    private final org.example.abonnement_service.client.UserClient userClient;

    @Override
    @Transactional
    public AbonnementResponse souscrire(AbonnementRequest request) {
        // 0. Vérifier si l'utilisateur existe
        try {
            userClient.getUserById(request.getUserId());
        } catch (feign.FeignException.NotFound e) {
            throw new org.example.common.exception.ResourceNotFoundException("User not found with ID : " + request.getUserId());
        }

        // 1. Vérifier si l'utilisateur n'a pas déjà un abonnement actif
        if (abonnementRepository.existsByUserIdAndStatut(request.getUserId(), StatutAbonnement.ACTIF)) {
            throw new IllegalStateException("L'utilisateur a déjà un abonnement actif.");
        }

        // 2. Récupérer le plan d'abonnement
        PlanAbonnement plan = planAbonnementRepository.findById(request.getPlanId())
                .orElseThrow(() -> new EntityNotFoundException("Plan d'abonnement introuvable."));

        if (plan.getEstActif() != StatutOffre.ACTIF) {
            throw new IllegalStateException("Ce plan d'abonnement n'est plus commercialisé.");
        }

        // 3. Calculer la date de fin initiale
        LocalDate dateDebut = LocalDate.now();
        LocalDate dateFin = calculerDateFin(dateDebut, plan.getDuree());

        // 4. Créer et sauvegarder l'abonnement
        Abonnement abonnement = Abonnement.builder()
                .userId(request.getUserId())
                .planAbonnement(plan)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .statut(StatutAbonnement.ACTIF)
                .prixPaye(plan.getPrix())
                .paiementId(request.getPaiementId())
                .build();

        Abonnement savedAbonnement = abonnementRepository.save(abonnement);

        // 5. Retourner le DTO de réponse
        return abonnementMapper.toResponse(savedAbonnement);
    }

    @Override
    @Transactional
    public void suspendre(Long idAbonnement, String motif) {
        Abonnement abonnement = findEntityById(idAbonnement);

        if (abonnement.getStatut() != StatutAbonnement.ACTIF) {
            throw new IllegalStateException("Seul un abonnement actif peut être suspendu.");
        }

        // Créer l'enregistrement de désactivation temporaire
        int maxJours = abonnement.getPlanAbonnement().getMaxPeriodeDesactivation();
        LocalDate debut = LocalDate.now();
        LocalDate fin = debut.plusDays(maxJours);

        Desactivation desactivation = Desactivation.builder()
                .dateDebutDesactivation(debut)
                .dateFinDesactivation(fin)
                .abonnement(abonnement)
                .build();

        desactivationRepository.save(desactivation);

        // Mettre à jour le statut
        abonnement.setStatut(StatutAbonnement.SUSPENDU);
        abonnementRepository.save(abonnement);
    }

    @Override
    @Transactional
    public boolean verifierExpiration(Long idAbonnement) {
        Abonnement abonnement = findEntityById(idAbonnement);

        if (abonnement.getStatut() == StatutAbonnement.ACTIF && LocalDate.now().isAfter(abonnement.getDateFin())) {
            abonnement.setStatut(StatutAbonnement.EXPIRE);
            abonnementRepository.save(abonnement);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void renouveler(Long idAbonnement, Long paiementId) {
        Abonnement abonnement = findEntityById(idAbonnement);

        // Calculer la nouvelle date de fin à partir de la date la plus tardive (fin de l'abo ou aujourd'hui)
        LocalDate referenceDate = abonnement.getDateFin().isAfter(LocalDate.now()) ? abonnement.getDateFin() : LocalDate.now();
        LocalDate nouvelleDateFin = calculerDateFin(referenceDate, abonnement.getPlanAbonnement().getDuree());

        // Mettre à jour l'abonnement
        abonnement.setDateFin(nouvelleDateFin);
        abonnement.setStatut(StatutAbonnement.ACTIF);

        // Enregistrer l'historique du renouvellement
        Renouvellement renouvellement = Renouvellement.builder()
                .dateRenouvellement(LocalDate.now())
                .typeRenouvellement(TypeRenouvellement.AUTOMATIQUE)
                .statut(StatutRenouvellement.SUCCES)
                .prixApplique(abonnement.getPlanAbonnement().getPrix())
                .paiementId(paiementId)
                .abonnement(abonnement)
                .build();

        renouvellementRepository.save(renouvellement);
        abonnementRepository.save(abonnement);
    }

    @Override
    @Transactional
    public void upgrader(Long idAbonnement, Long nouveauPlanId) {
        Abonnement abonnement = findEntityById(idAbonnement);
        PlanAbonnement nouveauPlan = planAbonnementRepository.findById(nouveauPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Nouveau plan introuvable."));

        // Remplacement immédiat du plan et ajustement de la date de fin
        abonnement.setPlanAbonnement(nouveauPlan);
        abonnement.setDateFin(calculerDateFin(LocalDate.now(), nouveauPlan.getDuree()));
        abonnement.setPrixPaye(nouveauPlan.getPrix());

        abonnementRepository.save(abonnement);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifierLimiteCommandesMois(Long idAbonnement, int commandesEffectuees) {
        Abonnement abonnement = findEntityById(idAbonnement);
        int limite = abonnement.getPlanAbonnement().getLimiteCommandesMois();

        if (limite <= 0) return true; // Illimité

        return commandesEffectuees < limite;
    }

    @Override
    @Transactional(readOnly = true)
    public int getDureeRestante(Long idAbonnement) {
        Abonnement abonnement = findEntityById(idAbonnement);
        if (abonnement.getStatut() != StatutAbonnement.ACTIF) {
            return 0;
        }
        long joursRestants = ChronoUnit.DAYS.between(LocalDate.now(), abonnement.getDateFin());
        return Math.max(0, (int) joursRestants);
    }

    @Override
    @Transactional(readOnly = true)
    public AbonnementResponse getAbonnementById(Long idAbonnement) {
        Abonnement abonnement = findEntityById(idAbonnement);
        return abonnementMapper.toResponse(abonnement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbonnementResponse> getHistoriqueUtilisateur(Long userId) {
        List<Abonnement> historique = abonnementRepository.findByUserId(userId);
        return abonnementMapper.toResponseList(historique);
    }

    // ==========================================
    // HELPERS & INTERNAL LOGIC
    // ==========================================

    /**
     * Méthode d'aide interne pour récupérer l'entité JPA sans l'exposer à l'extérieur du service.
     */
    private Abonnement findEntityById(Long idAbonnement) {
        return abonnementRepository.findById(idAbonnement)
                .orElseThrow(() -> new EntityNotFoundException("Abonnement introuvable avec l'ID : " + idAbonnement));
    }

    /**
     * Calcule la date de fin d'un abonnement selon sa récurrence.
     */
    private LocalDate calculerDateFin(LocalDate dateDebut, DureeOffre duree) {
        return switch (duree) {
            case HEBDOMADAIRE -> dateDebut.plusWeeks(1);
            case MENSUEL -> dateDebut.plusMonths(1);
            case TRIMESTRIEL -> dateDebut.plusMonths(3);
            case ANNUEL -> dateDebut.plusYears(1);
        };
    }
}