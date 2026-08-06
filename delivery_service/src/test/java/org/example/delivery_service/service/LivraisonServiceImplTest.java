package org.example.delivery_service.service;

import org.example.common.security.TenantContext;
import org.example.delivery_service.client.UserClient;
import org.example.delivery_service.client.UserSummary;
import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LivraisonServiceImplTest {

    @Mock private LivraisonRepository repository;
    @Mock private TransporteurStrategyFactory strategyFactory;
    @Mock private LivraisonMapper mapper;
    @Mock private LivraisonEventProducer eventProducer;
    @Mock private UserClient userClient;
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
        when(strategyFactory.getStrategy(TypeTransporteur.LIVREUR_INTERNE)).thenReturn(strategy);
        when(repository.save(any(Livraison.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.expedierLivraison(request);

        ArgumentCaptor<Livraison> shipment = ArgumentCaptor.forClass(Livraison.class);
        verify(repository).save(shipment.capture());
        assertThat(shipment.getValue().getEnterpriseId()).isEqualTo(42L);
        assertThat(shipment.getValue().getLivreurId()).isEqualTo(19L);
        assertThat(shipment.getValue().getStatutLivraison()).isEqualTo(StatutLivraison.EN_PREPARATION);
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

    private ExpedierLivraisonRequest internalRequest(Long courierId) {
        ExpedierLivraisonRequest request = new ExpedierLivraisonRequest();
        request.setReferenceCommandeId(12L);
        request.setTypeTransporteur(TypeTransporteur.LIVREUR_INTERNE);
        request.setLivreurId(courierId);
        return request;
    }

    private void authenticateCourier() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "courier@example.test",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_LIVREUR"))));
    }
}
