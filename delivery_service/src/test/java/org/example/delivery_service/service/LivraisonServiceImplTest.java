package org.example.delivery_service.service;

import org.example.common.security.TenantContext;
import org.example.delivery_service.client.UserClient;
import org.example.delivery_service.client.UserSummary;
import org.example.delivery_service.client.OrderClient;
import org.example.delivery_service.client.PaymentClient;
import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.CompleteDeliveryRequest;
import org.example.delivery_service.dto.request.FailedDeliveryAttemptRequest;
import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
import org.example.delivery_service.entity.MotifEchecLivraison;
import org.example.delivery_service.event.LivraisonEventProducer;
import org.example.delivery_service.mapper.LivraisonMapper;
import org.example.delivery_service.repository.LivraisonRepository;
import org.example.delivery_service.strategy.TransporteurStrategy;
import org.example.delivery_service.strategy.TransporteurStrategyFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LivraisonServiceImplTest {

    @Mock private LivraisonRepository repository;
    @Mock private TransporteurStrategyFactory strategyFactory;
    @Mock private LivraisonMapper mapper;
    @Mock private LivraisonEventProducer eventProducer;
    @Mock private UserClient userClient;
    @Mock private OrderClient orderClient;
    @Mock private PaymentClient paymentClient;
    @Mock private DeliveryProofStorage proofStorage;
    @Mock private TransporteurStrategy strategy;
    @InjectMocks private LivraisonServiceImpl service;

    @BeforeEach
    void setUpTenant() {
        TenantContext.setEnterpriseId(42L);
        TenantContext.setUserId(7L);
    }

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsInternalShipmentOnlyForAnActiveCourierAndKeepsItAwaitingAcceptance() {
        ExpedierLivraisonRequest request = internalRequest(19L);
        when(userClient.getActiveCourier(19L)).thenReturn(new UserSummary(19L, "ROLE_LIVREUR", true));
        when(orderClient.getOrder(12L)).thenReturn(shippableOrder());
        when(strategyFactory.getStrategy(TypeTransporteur.LIVREUR_INTERNE)).thenReturn(strategy);
        when(repository.save(any(Livraison.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.expedierLivraison(request);

        ArgumentCaptor<Livraison> shipment = ArgumentCaptor.forClass(Livraison.class);
        verify(repository).save(shipment.capture());
        assertThat(shipment.getValue().getEnterpriseId()).isEqualTo(42L);
        assertThat(shipment.getValue().getLivreurId()).isEqualTo(19L);
        assertThat(shipment.getValue().getStatutLivraison()).isEqualTo(StatutLivraison.ASSIGNEE);
    }

    @Test
    void rejectsAUserWhoIsNotAnActiveCourier() {
        ExpedierLivraisonRequest request = internalRequest(19L);
        when(userClient.getActiveCourier(19L)).thenReturn(new UserSummary(19L, "ROLE_LOGISTIC", true));

        assertThatThrownBy(() -> service.expedierLivraison(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active internal courier");
    }

    @Test
    void scopesCourierSearchToTheAuthenticatedCourierId() {
        authenticateCourier();
        when(repository.search(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.search(null, null, 0, 20);

        verify(repository).search(any(), any(), any(), org.mockito.ArgumentMatchers.eq(7L), any(Pageable.class));
    }

    @Test
    void preventsCourierFromOpeningAnotherCouriersDelivery() {
        authenticateCourier();
        Livraison shipment = Livraison.builder().enterpriseId(42L).livreurId(99L).build();
        when(repository.findByIdLivraisonAndEnterpriseId(5L, 42L)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.getById(5L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void courierAcceptsOnlyTheirAssignedInternalDelivery() {
        authenticateCourier();
        Livraison shipment = assignedShipment(StatutLivraison.ASSIGNEE);
        when(repository.findByIdLivraisonAndEnterpriseId(5L, 42L)).thenReturn(Optional.of(shipment));
        when(repository.save(shipment)).thenReturn(shipment);

        service.acceptAssignment(5L);

        assertThat(shipment.getStatutLivraison()).isEqualTo(StatutLivraison.ACCEPTEE);
        assertThat(shipment.getAcceptedAt()).isNotNull();
    }

    @Test
    void startingDeliveryHandsTheOrderToShippedAndPersistsTheStart() {
        authenticateCourier();
        Livraison shipment = assignedShipment(StatutLivraison.ACCEPTEE);
        when(repository.findByIdLivraisonAndEnterpriseId(5L, 42L)).thenReturn(Optional.of(shipment));
        when(repository.save(shipment)).thenReturn(shipment);

        service.startDelivery(5L);

        ArgumentCaptor<OrderClient.StatusUpdate> orderStatus =
                ArgumentCaptor.forClass(OrderClient.StatusUpdate.class);
        verify(orderClient).updateFulfillmentStatus(
                org.mockito.ArgumentMatchers.eq(12L), orderStatus.capture());
        assertThat(orderStatus.getValue().status()).isEqualTo("EXPEDIEE");
        assertThat(shipment.getStatutLivraison()).isEqualTo(StatutLivraison.EN_COURS);
        assertThat(shipment.getStartedAt()).isNotNull();
        verify(repository).save(shipment);
    }

    @Test
    void failedAttemptRecordsReasonNoteAndAuditCount() {
        authenticateCourier();
        Livraison shipment = assignedShipment(StatutLivraison.EN_COURS);
        when(repository.findByIdLivraisonAndEnterpriseId(5L, 42L)).thenReturn(Optional.of(shipment));
        when(repository.save(shipment)).thenReturn(shipment);

        service.reportFailedAttempt(5L, new FailedDeliveryAttemptRequest(
                MotifEchecLivraison.CLIENT_ABSENT, "Called twice", 33.57, -7.59));

        assertThat(shipment.getStatutLivraison()).isEqualTo(StatutLivraison.ECHEC);
        assertThat(shipment.getFailureReason()).isEqualTo(MotifEchecLivraison.CLIENT_ABSENT);
        assertThat(shipment.getAttemptCount()).isEqualTo(1);
        assertThat(shipment.getLastAttemptAt()).isNotNull();
    }

    @Test
    void codDeliveryCannotCompleteWithACollectedAmountMismatch() {
        authenticateCourier();
        Livraison shipment = assignedShipment(StatutLivraison.EN_COURS);
        shipment.setMontantACollecterCoD(120.00);
        when(repository.findByIdLivraisonAndEnterpriseId(5L, 42L)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.completeDelivery(5L,
                new CompleteDeliveryRequest("Customer", "Customer", new BigDecimal("100.00"), null, null, null),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collected COD amount");

        verify(paymentClient, never()).collectCashOnDelivery(any());
        verify(repository, never()).save(any());
    }

    private ExpedierLivraisonRequest internalRequest(Long courierId) {
        ExpedierLivraisonRequest request = new ExpedierLivraisonRequest();
        request.setReferenceCommandeId(12L);
        request.setTypeTransporteur(TypeTransporteur.LIVREUR_INTERNE);
        request.setLivreurId(courierId);
        return request;
    }

    private OrderClient.OrderSummary shippableOrder() {
        return new OrderClient.OrderSummary(
                12L,
                new BigDecimal("120.00"),
                "PREPARATION",
                "PAID",
                new OrderClient.CustomerSummary(
                        "Customer", "customer@example.test", "+212600000000",
                        "1 Test Street", "Casablanca"));
    }

    private void authenticateCourier() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "courier@example.test",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_LIVREUR"))));
    }

    private Livraison assignedShipment(StatutLivraison status) {
        return Livraison.builder()
                .idLivraison(5L)
                .enterpriseId(42L)
                .referenceCommandeId(12L)
                .livreurId(7L)
                .typeTransporteur(TypeTransporteur.LIVREUR_INTERNE)
                .statutLivraison(status)
                .montantACollecterCoD(0)
                .build();
    }
}
