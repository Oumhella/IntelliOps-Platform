package org.example.abonnement_service.service;

import org.example.abonnement_service.client.PaymentClient;
import org.example.abonnement_service.dto.request.AbonnementRequest;
import org.example.abonnement_service.dto.request.SubscriptionCheckoutRequest;
import org.example.abonnement_service.dto.request.CompletePaymentCheckoutRequest;
import org.example.abonnement_service.dto.response.AbonnementResponse;
import org.example.abonnement_service.entity.*;
import org.example.abonnement_service.event.AbonnementEventProducer;
import org.example.abonnement_service.mapper.AbonnementMapper;
import org.example.abonnement_service.repository.*;
import org.example.common.exception.PaymentRequiredException;
import org.example.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbonnementServiceImplTest {
    private final AbonnementRepository subscriptions = mock(AbonnementRepository.class);
    private final PlanAbonnementRepository plans = mock(PlanAbonnementRepository.class);
    private final RenouvellementRepository renewals = mock(RenouvellementRepository.class);
    private final DesactivationRepository pauses = mock(DesactivationRepository.class);
    private final ChangementPlanRepository planChanges = mock(ChangementPlanRepository.class);
    private final AbonnementMapper mapper = mock(AbonnementMapper.class);
    private final AbonnementEventProducer events = mock(AbonnementEventProducer.class);
    private final PaymentClient payments = mock(PaymentClient.class);
    private final AbonnementServiceImpl service = new AbonnementServiceImpl(
            subscriptions, plans, renewals, pauses, planChanges, mapper, events, payments);

    @BeforeEach
    void tenant() {
        TenantContext.setEnterpriseId(42L);
        TenantContext.setUserId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void paidPlanCannotActivateWithoutPayment() {
        when(plans.findById(3L)).thenReturn(Optional.of(paidPlan()));

        assertThrows(PaymentRequiredException.class,
                () -> service.souscrire(new AbonnementRequest(3L, null)));

        verify(payments, never()).consume(anyLong(), any());
        verify(subscriptions, never()).save(any());
    }

    @Test
    void checkoutPreparationUsesTheServerPlanPriceWithoutActivating() {
        PlanAbonnement plan = paidPlan();
        when(plans.findById(3L)).thenReturn(Optional.of(plan));
        when(payments.prepare(any())).thenReturn(new PaymentClient.PaymentPreparation(
                10L, "pi_secret", "pk_test_key", new BigDecimal("99.00"), "mad", "PENDING"));

        service.prepareCheckout(new SubscriptionCheckoutRequest(3L, "checkout-abc"));

        ArgumentCaptor<PaymentClient.PreparePayment> charge = ArgumentCaptor.forClass(PaymentClient.PreparePayment.class);
        verify(payments).prepare(charge.capture());
        assertEquals(new BigDecimal("99.00"), charge.getValue().montant());
        assertEquals(3L, charge.getValue().referenceSourceId());
        verify(subscriptions, never()).save(any());
        verify(payments, never()).consume(anyLong(), any());
    }

    @Test
    void completedStripeIntentIsConsumedBeforeActivation() {
        PlanAbonnement plan = paidPlan();
        when(plans.findById(3L)).thenReturn(Optional.of(plan));
        when(payments.finalizePayment(10L)).thenReturn(new PaymentClient.PaymentSummary(
                10L, 3L, "ABONNEMENT_PLATFORM", new BigDecimal("99.00"), "COMPLETED", null));
        when(payments.consume(eq(10L), any())).thenReturn(new PaymentClient.PaymentSummary(
                10L, 3L, "ABONNEMENT_PLATFORM", new BigDecimal("99.00"), "COMPLETED",
                "subscription:activate:42:3"));
        when(subscriptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Abonnement.class))).thenReturn(new AbonnementResponse());

        service.completeCheckout(new CompletePaymentCheckoutRequest(10L));

        verify(payments).finalizePayment(10L);
        verify(payments).consume(eq(10L), any());
        verify(subscriptions).save(argThat(subscription -> subscription.getPaiementId().equals(10L)
                && subscription.getStatut() == StatutAbonnement.ACTIF));
    }

    @Test
    void checkoutDoesNotActivateWhenStripeHasNotCompletedPayment() {
        when(payments.finalizePayment(10L)).thenReturn(new PaymentClient.PaymentSummary(
                10L, 3L, "ABONNEMENT_PLATFORM", new BigDecimal("99.00"), "PENDING", null));

        assertThrows(PaymentRequiredException.class, () -> service.completeCheckout(
                new CompletePaymentCheckoutRequest(10L)));
        verify(subscriptions, never()).save(any());
    }

    private PlanAbonnement paidPlan() {
        return PlanAbonnement.builder()
                .idPlan(3L)
                .nomPlan("Business")
                .prix(99.0)
                .duree(DureeOffre.MENSUEL)
                .estActif(StatutOffre.ACTIF)
                .build();
    }
}
