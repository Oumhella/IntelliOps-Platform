package org.example.abonnement_service.service;

import jakarta.persistence.EntityNotFoundException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.example.abonnement_service.client.PaymentClient;
import org.example.abonnement_service.dto.request.AbonnementRequest;
import org.example.abonnement_service.dto.request.PaymentCheckoutRequest;
import org.example.abonnement_service.dto.request.SubscriptionCheckoutRequest;
import org.example.abonnement_service.dto.request.UpgradeCheckoutRequest;
import org.example.abonnement_service.dto.request.CompletePaymentCheckoutRequest;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.example.abonnement_service.dto.response.CheckoutPreparationResponse;
import org.example.abonnement_service.entity.*;
import org.example.abonnement_service.event.AbonnementEventProducer;
import org.example.abonnement_service.mapper.AbonnementMapper;
import org.example.abonnement_service.repository.*;
import org.example.common.dto.PageResponse;
import org.example.common.exception.PaymentRequiredException;
import org.example.common.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbonnementServiceImpl implements AbonnementService {

    private static final String PAYMENT_CONTEXT = "ABONNEMENT_PLATFORM";
    private final AbonnementRepository abonnementRepository;
    private final PlanAbonnementRepository planAbonnementRepository;
    private final RenouvellementRepository renouvellementRepository;
    private final DesactivationRepository desactivationRepository;
    private final ChangementPlanRepository changementPlanRepository;
    private final AbonnementMapper abonnementMapper;
    private final AbonnementEventProducer eventProducer;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    public CheckoutPreparationResponse prepareCheckout(SubscriptionCheckoutRequest request) {
        PlanAbonnement plan = requireCommercialPlan(request.planId());
        if (isFree(plan)) {
            throw new IllegalArgumentException("A free plan does not require a Stripe checkout.");
        }
        return prepareCheckoutPayment(plan, request.idempotencyKey());
    }

    @Override
    @Transactional
    public AbonnementResponse completeCheckout(CompletePaymentCheckoutRequest request) {
        PaymentClient.PaymentSummary payment = finalizeCheckoutPayment(request.paymentId());
        requireCaptured(payment);
        return souscrire(new AbonnementRequest(payment.referenceSourceId(), payment.id()));
    }

    @Override
    @Transactional
    public AbonnementResponse souscrire(AbonnementRequest request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        Long accountOwnerId = TenantContext.requireUserId();

        if (request.getPaiementId() != null) {
            var previousResult = abonnementRepository.findByPaiementIdAndEnterpriseId(
                    request.getPaiementId(), enterpriseId);
            if (previousResult.isPresent()) {
                if (!previousResult.get().getPlanAbonnement().getIdPlan().equals(request.getPlanId())) {
                    throw new IllegalStateException("This payment is already attached to another subscription plan.");
                }
                return abonnementMapper.toResponse(previousResult.get());
            }
        }

        if (abonnementRepository.existsByEnterpriseIdAndStatutIn(
                enterpriseId, List.of(StatutAbonnement.ACTIF, StatutAbonnement.SUSPENDU))) {
            throw new IllegalStateException("The enterprise already has a current subscription.");
        }

        PlanAbonnement plan = requireCommercialPlan(request.getPlanId());
        if (!isFree(plan)) {
            if (request.getPaiementId() == null) {
                throw new PaymentRequiredException("A completed payment is required for this paid plan.");
            }
            consumePayment(request.getPaiementId(), plan,
                    "subscription:activate:" + enterpriseId + ":" + plan.getIdPlan());
        } else if (request.getPaiementId() != null) {
            throw new IllegalArgumentException("A free plan must not consume a paid transaction.");
        }

        LocalDate start = LocalDate.now();
        Abonnement subscription = Abonnement.builder()
                .enterpriseId(enterpriseId)
                .userId(accountOwnerId)
                .planAbonnement(plan)
                .dateDebut(start)
                .dateFin(calculerDateFin(start, plan.getDuree()))
                .statut(StatutAbonnement.ACTIF)
                .prixPaye(plan.getPrix())
                .paiementId(request.getPaiementId())
                .build();

        Abonnement saved = abonnementRepository.save(subscription);
        eventProducer.sendSubscriptionNotification(
                authenticatedEmail(),
                "Your " + plan.getNomPlan() + " subscription is active",
                "Your workspace subscription is active through " + saved.getDateFin() + ".");
        return abonnementMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void suspendre(Long idAbonnement, String motif) {
        Abonnement abonnement = findEntityById(idAbonnement);
        if (abonnement.getStatut() != StatutAbonnement.ACTIF) {
            throw new IllegalStateException("Only an active subscription can be paused.");
        }
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("A suspension reason is required.");
        }
        int maxDays = abonnement.getPlanAbonnement().getMaxPeriodeDesactivation();
        if (maxDays <= 0) {
            throw new IllegalStateException("This plan does not allow temporary suspension.");
        }
        LocalDate start = LocalDate.now();
        int minimumInterval = abonnement.getPlanAbonnement().getMinJoursEntreDesactivation();
        desactivationRepository.findTopByAbonnementIdAbonnementOrderByDateFinDesactivationDesc(idAbonnement)
                .ifPresent(previous -> {
                    LocalDate nextAllowed = previous.getDateFinDesactivation().plusDays(minimumInterval);
                    if (start.isBefore(nextAllowed)) {
                        throw new IllegalStateException("The minimum interval between subscription pauses has not elapsed.");
                    }
                });
        desactivationRepository.save(Desactivation.builder()
                .dateDebutDesactivation(start)
                .dateFinDesactivation(start.plusDays(maxDays))
                .motif(motif.trim())
                .abonnement(abonnement)
                .build());
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
    public CheckoutPreparationResponse prepareRenewalCheckout(Long idAbonnement, PaymentCheckoutRequest request) {
        Abonnement abonnement = findEntityById(idAbonnement);
        PlanAbonnement plan = abonnement.getPlanAbonnement();
        if (isFree(plan)) {
            throw new IllegalStateException("Free-plan renewal requires an explicit platform policy, not a payment checkout.");
        }
        return prepareCheckoutPayment(plan, request.idempotencyKey());
    }

    @Override
    @Transactional
    public AbonnementResponse completeRenewalCheckout(
            Long idAbonnement, CompletePaymentCheckoutRequest request) {
        PaymentClient.PaymentSummary payment = finalizeCheckoutPayment(request.paymentId());
        requireCaptured(payment);
        return renouveler(idAbonnement, payment.id());
    }

    @Override
    @Transactional
    public AbonnementResponse renouveler(Long idAbonnement, Long paiementId) {
        if (paiementId == null) {
            throw new PaymentRequiredException("A completed payment is required to renew a subscription.");
        }
        Abonnement abonnement = findEntityById(idAbonnement);
        var previousRenewal = renouvellementRepository.findByPaiementId(paiementId);
        if (previousRenewal.isPresent()) {
            if (!previousRenewal.get().getAbonnement().getIdAbonnement().equals(idAbonnement)) {
                throw new IllegalStateException("This payment has already renewed another subscription.");
            }
            return abonnementMapper.toResponse(abonnement);
        }
        if (abonnement.getStatut() == StatutAbonnement.ANNULE
                || abonnement.getStatut() == StatutAbonnement.ANNULATION_EN_COURS) {
            throw new IllegalStateException("A cancelled subscription cannot be renewed.");
        }

        PlanAbonnement plan = abonnement.getPlanAbonnement();
        consumePayment(paiementId, plan,
                "subscription:renew:" + abonnement.getEnterpriseId() + ":" + idAbonnement);

        LocalDate reference = abonnement.getDateFin().isAfter(LocalDate.now())
                ? abonnement.getDateFin() : LocalDate.now();
        abonnement.setDateFin(calculerDateFin(reference, plan.getDuree()));
        abonnement.setStatut(StatutAbonnement.ACTIF);
        renouvellementRepository.save(Renouvellement.builder()
                .dateRenouvellement(LocalDate.now())
                .typeRenouvellement(TypeRenouvellement.MANUEL)
                .statut(StatutRenouvellement.SUCCES)
                .prixApplique(plan.getPrix())
                .paiementId(paiementId)
                .abonnement(abonnement)
                .build());
        return abonnementMapper.toResponse(abonnementRepository.save(abonnement));
    }

    @Override
    @Transactional
    public CheckoutPreparationResponse prepareUpgradeCheckout(Long idAbonnement, UpgradeCheckoutRequest request) {
        PlanAbonnement newPlan = requireUpgrade(findEntityById(idAbonnement), request.newPlanId());
        return prepareCheckoutPayment(newPlan, request.idempotencyKey());
    }

    @Override
    @Transactional
    public AbonnementResponse completeUpgradeCheckout(
            Long idAbonnement, CompletePaymentCheckoutRequest request) {
        PaymentClient.PaymentSummary payment = finalizeCheckoutPayment(request.paymentId());
        requireCaptured(payment);
        return upgrader(idAbonnement, payment.referenceSourceId(), payment.id());
    }

    @Override
    @Transactional
    public AbonnementResponse upgrader(Long idAbonnement, Long nouveauPlanId, Long paiementId) {
        if (paiementId == null) {
            throw new PaymentRequiredException("A completed payment is required to upgrade a subscription.");
        }
        Abonnement abonnement = findEntityById(idAbonnement);
        var previousChange = changementPlanRepository.findByPaiementId(paiementId);
        if (previousChange.isPresent()) {
            ChangementPlan change = previousChange.get();
            if (!change.getAbonnement().getIdAbonnement().equals(idAbonnement)
                    || !change.getNouveauPlanId().equals(nouveauPlanId)) {
                throw new IllegalStateException("This payment has already been used by another plan change.");
            }
            return abonnementMapper.toResponse(abonnement);
        }

        PlanAbonnement newPlan = requireUpgrade(abonnement, nouveauPlanId);
        consumePayment(paiementId, newPlan,
                "subscription:upgrade:" + abonnement.getEnterpriseId() + ":" + idAbonnement
                        + ":" + nouveauPlanId);

        Long oldPlanId = abonnement.getPlanAbonnement().getIdPlan();
        abonnement.setPlanAbonnement(newPlan);
        abonnement.setDateDebut(LocalDate.now());
        abonnement.setDateFin(calculerDateFin(LocalDate.now(), newPlan.getDuree()));
        abonnement.setPrixPaye(newPlan.getPrix());
        abonnementRepository.save(abonnement);
        changementPlanRepository.save(ChangementPlan.builder()
                .ancienPlanId(oldPlanId)
                .nouveauPlanId(newPlan.getIdPlan())
                .paiementId(paiementId)
                .montant(newPlan.getPrix())
                .changedAt(LocalDateTime.now())
                .abonnement(abonnement)
                .build());
        return abonnementMapper.toResponse(abonnement);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifierLimiteCommandesMois(Long idAbonnement, int commandesEffectuees) {
        Abonnement abonnement = findEntityById(idAbonnement);
        int limit = abonnement.getPlanAbonnement().getLimiteCommandesMois();
        return limit <= 0 || commandesEffectuees < limit;
    }

    @Override
    @Transactional(readOnly = true)
    public int getDureeRestante(Long idAbonnement) {
        Abonnement abonnement = findEntityById(idAbonnement);
        if (abonnement.getStatut() != StatutAbonnement.ACTIF) return 0;
        return Math.max(0, (int) ChronoUnit.DAYS.between(LocalDate.now(), abonnement.getDateFin()));
    }

    @Override
    @Transactional(readOnly = true)
    public AbonnementResponse getAbonnementById(Long idAbonnement) {
        return abonnementMapper.toResponse(findEntityById(idAbonnement));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbonnementResponse> getHistoriqueUtilisateur(Long userId) {
        return abonnementMapper.toResponseList(abonnementRepository.findByUserIdAndEnterpriseId(
                userId, TenantContext.requireEnterpriseId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AbonnementResponse> search(StatutAbonnement statut, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "dateDebut"));
        Long enterpriseId = TenantContext.requireEnterpriseId();
        Page<Abonnement> result = statut == null
                ? abonnementRepository.findAllByEnterpriseId(enterpriseId, pageable)
                : abonnementRepository.findAllByEnterpriseIdAndStatut(enterpriseId, statut, pageable);
        return PageResponse.from(result, abonnementMapper::toResponse);
    }

    private CheckoutPreparationResponse prepareCheckoutPayment(PlanAbonnement plan, String idempotencyKey) {
        try {
            PaymentClient.PaymentPreparation prepared = paymentClient.prepare(new PaymentClient.PreparePayment(
                    idempotencyKey.trim(), plan.getIdPlan(), PAYMENT_CONTEXT, money(plan.getPrix())));
            return new CheckoutPreparationResponse(prepared.paymentId(), prepared.clientSecret(),
                    prepared.publishableKey(), prepared.amount(), prepared.currency());
        } catch (FeignException exception) {
            if (exception.contentUTF8().contains("Stripe publishable key is not configured")) {
                throw new IllegalStateException(
                        "Stripe checkout is not configured: Vault has the secret API key, but the paired STRIPE_PUBLISHABLE_KEY (pk_test_... or pk_live_...) is missing.");
            }
            throw new PaymentRequiredException("The payment provider could not prepare this checkout.");
        }
    }

    private PaymentClient.PaymentSummary finalizeCheckoutPayment(Long paymentId) {
        try {
            return paymentClient.finalizePayment(paymentId);
        } catch (FeignException exception) {
            throw new PaymentRequiredException("Stripe has not confirmed this payment.");
        }
    }

    private void consumePayment(Long paymentId, PlanAbonnement plan, String operationReference) {
        PaymentClient.PaymentSummary consumed;
        try {
            consumed = paymentClient.consume(paymentId,
                    new PaymentClient.ConsumePayment(PAYMENT_CONTEXT, plan.getIdPlan(), money(plan.getPrix()),
                            operationReference));
        } catch (FeignException exception) {
            throw new PaymentRequiredException(
                    "The referenced payment is missing, incomplete, mismatched, or already used.");
        }
        requireCaptured(consumed);
        if (!operationReference.equals(consumed.consumptionReference())) {
            throw new IllegalStateException("Payment consumption was not recorded for this subscription operation.");
        }
    }

    private void requireCaptured(PaymentClient.PaymentSummary payment) {
        if (payment == null || !"COMPLETED".equals(payment.statut())) {
            Long paymentId = payment == null ? null : payment.id();
            throw new PaymentRequiredException(
                    "The card payment has not completed" + (paymentId == null ? "." : " (payment #" + paymentId + ")."));
        }
    }

    private PlanAbonnement requireCommercialPlan(Long planId) {
        PlanAbonnement plan = planAbonnementRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription plan not found."));
        if (plan.getEstActif() != StatutOffre.ACTIF) {
            throw new IllegalStateException("The selected subscription plan is not commercially available.");
        }
        return plan;
    }

    private PlanAbonnement requireUpgrade(Abonnement abonnement, Long newPlanId) {
        if (abonnement.getStatut() != StatutAbonnement.ACTIF) {
            throw new IllegalStateException("Only an active subscription can be upgraded.");
        }
        PlanAbonnement newPlan = requireCommercialPlan(newPlanId);
        if (newPlan.getIdPlan().equals(abonnement.getPlanAbonnement().getIdPlan())) {
            throw new IllegalStateException("The subscription already uses this plan.");
        }
        if (newPlan.getPrix() <= abonnement.getPlanAbonnement().getPrix()) {
            throw new IllegalStateException("An immediate upgrade must target a higher-priced plan.");
        }
        return newPlan;
    }

    private boolean isFree(PlanAbonnement plan) {
        return plan.getPrix() == null || plan.getPrix() <= 0;
    }

    private BigDecimal money(Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("The plan must have a positive price for payment checkout.");
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private String authenticatedEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "billing@intelliops.local" : authentication.getName();
    }

    private Abonnement findEntityById(Long idAbonnement) {
        return abonnementRepository.findByIdAbonnementAndEnterpriseId(
                        idAbonnement, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found: " + idAbonnement));
    }

    private LocalDate calculerDateFin(LocalDate start, DureeOffre duration) {
        return switch (duration) {
            case HEBDOMADAIRE -> start.plusWeeks(1);
            case MENSUEL -> start.plusMonths(1);
            case TRIMESTRIEL -> start.plusMonths(3);
            case ANNUEL -> start.plusYears(1);
        };
    }
}
