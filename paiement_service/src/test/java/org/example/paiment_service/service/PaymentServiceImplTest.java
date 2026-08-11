package org.example.paiment_service.service;

import org.example.common.exception.PaymentRequiredException;
import org.example.common.security.TenantContext;
import org.example.paiment_service.dto.request.ConsumePaymentRequest;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.request.PreparePaymentRequestDTO;
import org.example.paiment_service.dto.response.PaymentPreparationResponseDTO;
import org.example.paiment_service.entity.*;
import org.example.paiment_service.event.PaymentEventProducer;
import org.example.paiment_service.gateway.PaymentGatewayFactory;
import org.example.paiment_service.gateway.PaymentGatewayProvider;
import org.example.paiment_service.mapper.PaymentMapper;
import org.example.paiment_service.repository.FactureRepository;
import org.example.paiment_service.repository.TransactionPaiementRepository;
import org.example.paiment_service.client.OrderClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {
    private final TransactionPaiementRepository transactions = mock(TransactionPaiementRepository.class);
    private final FactureRepository invoices = mock(FactureRepository.class);
    private final PaymentMapper mapper = mock(PaymentMapper.class);
    private final PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
    private final InvoicePdfService invoicePdfService = mock(InvoicePdfService.class);
    private final PaymentEventProducer events = mock(PaymentEventProducer.class);
    private final OrderClient orders = mock(OrderClient.class);
    private final PaymentServiceImpl service = new PaymentServiceImpl(
            transactions, invoices, mapper, gatewayFactory, invoicePdfService, events, orders);

    @BeforeEach
    void tenant() {
        TenantContext.setEnterpriseId(42L);
        TenantContext.setUserId(7L);
        ReflectionTestUtils.setField(service, "stripePublishableKey", "pk_test_browser");
        ReflectionTestUtils.setField(service, "stripeCurrency", "mad");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void incompletePaymentCannotBeConsumed() {
        TransactionPaiement payment = payment(StatutPaiement.PENDING);
        when(transactions.findForConsumption(10L, 42L)).thenReturn(Optional.of(payment));

        assertThrows(PaymentRequiredException.class, () -> service.consumeCompletedPayment(10L,
                new ConsumePaymentRequest(Contexte.ABONNEMENT_PLATFORM, 3L,
                        new BigDecimal("99.00"), "subscription:activate:42:3")));
        verify(transactions, never()).save(any());
    }

    @Test
    void completedPaymentIsValidatedAndConsumedOnce() {
        TransactionPaiement payment = payment(StatutPaiement.COMPLETED);
        when(transactions.findForConsumption(10L, 42L)).thenReturn(Optional.of(payment));
        when(transactions.save(payment)).thenReturn(payment);

        service.consumeCompletedPayment(10L,
                new ConsumePaymentRequest(Contexte.ABONNEMENT_PLATFORM, 3L,
                        new BigDecimal("99.00"), "subscription:activate:42:3"));

        assertEquals("subscription:activate:42:3", payment.getConsumptionReference());
        assertNotNull(payment.getConsumedAt());
        verify(transactions).save(payment);
    }

    @Test
    void preparationCreatesAPendingIntentWithoutReceivingCardData() {
        PaymentGatewayProvider provider = mock(PaymentGatewayProvider.class);
        when(transactions.findByIdempotencyKeyAndEnterpriseId("checkout-1", 42L)).thenReturn(Optional.empty());
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> {
            TransactionPaiement payment = invocation.getArgument(0);
            payment.setId(10L);
            return payment;
        });
        when(gatewayFactory.getProvider(ModePaiement.CREDIT_CARD)).thenReturn(provider);
        when(provider.preparerPaiement(new BigDecimal("99.00"), "checkout-1"))
                .thenReturn(new PaymentGatewayProvider.PreparedPayment(
                        "pi_123", "pi_123_secret", StatutPaiement.PENDING,
                        new BigDecimal("99.00"), "mad"));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentPreparationResponseDTO prepared = service.prepareCardPayment(new PreparePaymentRequestDTO(
                "checkout-1", 3L, Contexte.ABONNEMENT_PLATFORM, new BigDecimal("99.00")));

        assertEquals(10L, prepared.paymentId());
        assertEquals("pi_123_secret", prepared.clientSecret());
        assertEquals("pk_test_browser", prepared.publishableKey());
        verify(provider).preparerPaiement(new BigDecimal("99.00"), "checkout-1");
    }

    @Test
    void finalizationRetrievesAndValidatesStripeBeforeMarkingCompleted() {
        TransactionPaiement payment = payment(StatutPaiement.PENDING);
        payment.setProviderTransactionId("pi_123");
        PaymentGatewayProvider provider = mock(PaymentGatewayProvider.class);
        when(transactions.findForFinalization(10L, 42L)).thenReturn(Optional.of(payment));
        when(gatewayFactory.getProvider(ModePaiement.CREDIT_CARD)).thenReturn(provider);
        when(provider.verifierPaiement("pi_123")).thenReturn(new PaymentGatewayProvider.PaymentResult(
                "pi_123", StatutPaiement.COMPLETED, new BigDecimal("99.00"), "mad"));
        when(transactions.save(payment)).thenReturn(payment);

        service.finalizeCardPayment(10L);

        assertEquals(StatutPaiement.COMPLETED, payment.getStatut());
        verify(provider).verifierPaiement("pi_123");
        verify(events).sendPaymentNotification(anyString(), anyString(), anyString());
    }

    @Test
    void refundUsesProviderTransactionIdAndNotStoredCardData() {
        TransactionPaiement payment = payment(StatutPaiement.COMPLETED);
        payment.setProviderTransactionId("pi_real_123");
        PaymentGatewayProvider provider = mock(PaymentGatewayProvider.class);
        when(transactions.findByIdAndEnterpriseId(10L, 42L)).thenReturn(Optional.of(payment));
        when(gatewayFactory.getProvider(ModePaiement.CREDIT_CARD)).thenReturn(provider);
        when(provider.traiterRemboursement(eq("pi_real_123"), eq(new BigDecimal("25.00")), anyString()))
                .thenReturn(true);
        when(transactions.save(payment)).thenReturn(payment);
        RemboursementRequestDTO request = new RemboursementRequestDTO();
        request.setMontant(new BigDecimal("25.00"));
        request.setMotif("Customer request");

        service.rembourserPaiement(10L, request);

        assertEquals(new BigDecimal("25.00"), payment.getMontantRembourse());
        assertEquals(StatutPaiement.PARTIALLY_REFUNDED, payment.getStatut());
    }

    private TransactionPaiement payment(StatutPaiement status) {
        return TransactionPaiement.builder()
                .id(10L)
                .enterpriseId(42L)
                .idempotencyKey("checkout-1")
                .referenceSourceId(3L)
                .typeContexte(Contexte.ABONNEMENT_PLATFORM)
                .montant(new BigDecimal("99.00"))
                .montantRembourse(new BigDecimal("0.00"))
                .mode(ModePaiement.CREDIT_CARD)
                .notificationEmail("customer@example.test")
                .statut(status)
                .build();
    }
}
