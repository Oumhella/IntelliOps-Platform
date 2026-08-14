package org.example.delivery_service.service;

import org.example.common.exception.ResourceNotFoundException;
import org.example.delivery_service.client.UserClient;
import org.example.delivery_service.client.UserSummary;
import org.example.delivery_service.client.OrderClient;
import org.example.delivery_service.client.PaymentClient;
import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.UpdateStatutRequest;
import org.example.delivery_service.dto.request.AssignCourierRequest;
import org.example.delivery_service.dto.response.LivraisonResponse;
import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
import org.example.delivery_service.event.LivraisonEventProducer;
import org.example.delivery_service.mapper.LivraisonMapper;
import org.example.delivery_service.repository.LivraisonRepository;
import org.example.delivery_service.service.LivraisonService;
import org.example.delivery_service.strategy.TransporteurStrategy;
import org.example.delivery_service.strategy.TransporteurStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import org.example.common.dto.PageResponse;
import org.example.common.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import feign.FeignException;

@Service
@RequiredArgsConstructor
public class LivraisonServiceImpl implements LivraisonService {

    private final LivraisonRepository livraisonRepository;
    private final TransporteurStrategyFactory strategyFactory;
    private final LivraisonMapper livraisonMapper;
    private final LivraisonEventProducer eventProducer;
    private final UserClient userClient;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    public LivraisonResponse expedierLivraison(ExpedierLivraisonRequest request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        if (livraisonRepository.existsByReferenceCommandeIdAndEnterpriseId(
                request.getReferenceCommandeId(), enterpriseId)) {
            throw new IllegalArgumentException("Shipment already exists for order ID: " + request.getReferenceCommandeId());
        }
        validateTransporter(request);
        OrderClient.OrderSummary order = requireShippableOrder(request.getReferenceCommandeId());
        OrderClient.CustomerSummary customer = order.infosClient();
        double codAmount = "AWAITING_COLLECTION".equals(order.statutPaiement())
                ? order.totalPrix().doubleValue() : 0.0;

        Livraison livraison = Livraison.builder()
                .enterpriseId(enterpriseId)
                .referenceCommandeId(request.getReferenceCommandeId())
                .codeSuiviTracking("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .statutLivraison(StatutLivraison.EN_PREPARATION)
                .typeTransporteur(request.getTypeTransporteur())
                .nomSociete(request.getNomSociete())
                .livreurId(request.getLivreurId())
                .montantACollecterCoD(codAmount)
                .clientEmail(customer.email())
                .clientNomComplet(customer.nomComplet())
                .clientTelephone(customer.telephone())
                .adresseLivraison(customer.adresseLivraison())
                .villeLivraison(customer.ville())
                .shippingDate(LocalDateTime.now())
                .build();

        // Strategy Execution
        TransporteurStrategy strategy = strategyFactory.getStrategy(request.getTypeTransporteur());
        strategy.executerLivraison(livraison);

        Livraison saved = livraisonRepository.save(livraison);
        String emailSubject = "Livraison créée pour votre commande #" + saved.getReferenceCommandeId();
        String emailBody = String.format(
                "Bonjour, la livraison de votre commande #%d est préparée via %s. Code de suivi: %s",
                saved.getReferenceCommandeId(),
                saved.getTypeTransporteur(),
                saved.getCodeSuiviTracking()
        );

        if (saved.getClientEmail() != null && !saved.getClientEmail().isBlank()) {
            eventProducer.sendNotificationEvent(saved.getClientEmail(), emailSubject, emailBody);
        }

        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getById(Long id) {
        return livraisonMapper.toResponse(findDelivery(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LivraisonResponse> search(
            StatutLivraison statut, TypeTransporteur transporteur, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "shippingDate"));
        return PageResponse.from(
                livraisonRepository.search(
                        TenantContext.requireEnterpriseId(), statut, transporteur, currentCourierId(), pageable),
                livraisonMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getByTrackingNumber(String trackingNum) {
        Livraison livraison = livraisonRepository.findByCodeSuiviTrackingAndEnterpriseId(
                        trackingNum, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found with tracking code: " + trackingNum));
        ensureCourierAccess(livraison);
        return livraisonMapper.toResponse(livraison);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getByCommandeId(Long commandeId) {
        Livraison livraison = livraisonRepository.findByReferenceCommandeIdAndEnterpriseId(
                        commandeId, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found for order ID: " + commandeId));
        ensureCourierAccess(livraison);
        return livraisonMapper.toResponse(livraison);
    }

    @Override
    @Transactional
    public LivraisonResponse mettreAJourStatut(Long id, UpdateStatutRequest request) {
        Livraison livraison = findDelivery(id);
        ensureStatusOwnership(livraison);

        if (!isAllowedTransition(livraison, request.getStatut())) {
            throw new IllegalStateException(
                    "Invalid delivery transition from " + livraison.getStatutLivraison() + " to " + request.getStatut() + ".");
        }

        if (request.getStatut() == StatutLivraison.CHEZ_TRANSPORTEUR
                || request.getStatut() == StatutLivraison.EN_COURS) {
            orderClient.updateFulfillmentStatus(livraison.getReferenceCommandeId(),
                    new OrderClient.StatusUpdate("EXPEDIEE"));
        } else if (request.getStatut() == StatutLivraison.LIVREE) {
            completeOrderDelivery(livraison);
        } else if (request.getStatut() == StatutLivraison.RETOUR) {
            orderClient.updateFulfillmentStatus(livraison.getReferenceCommandeId(),
                    new OrderClient.StatusUpdate("RETOURNEE"));
        }

        livraison.mettreAJourStatut(request.getStatut());
        Livraison saved = livraisonRepository.save(livraison);
        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LivraisonResponse confirmerReception(Long id) {
        Livraison livraison = findDelivery(id);
        ensureStatusOwnership(livraison);

        if (livraison.getStatutLivraison() != StatutLivraison.EN_COURS
                && livraison.getStatutLivraison() != StatutLivraison.CHEZ_TRANSPORTEUR) {
            throw new IllegalStateException("Reception can only be confirmed for a dispatched delivery.");
        }

        completeOrderDelivery(livraison);
        livraison.mettreAJourStatut(StatutLivraison.LIVREE);
        Livraison saved = livraisonRepository.save(livraison);

        if (saved.getClientEmail() != null && !saved.getClientEmail().isBlank()) {
            eventProducer.sendNotificationEvent(
                    saved.getClientEmail(),
                    "Commande Livrée !",
                    "Votre commande #" + saved.getReferenceCommandeId() + " a bien été livrée. Merci !"
            );
        }

        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LivraisonResponse assignerLivreur(Long id, AssignCourierRequest request) {
        Livraison livraison = findDelivery(id);
        if (livraison.getTypeTransporteur() != TypeTransporteur.LIVREUR_INTERNE) {
            throw new IllegalStateException("Only an internal delivery can be assigned to a courier.");
        }
        if (livraison.getStatutLivraison() != StatutLivraison.EN_PREPARATION
                && livraison.getStatutLivraison() != StatutLivraison.ECHEC) {
            throw new IllegalStateException("A courier can be reassigned only before dispatch or after a failed attempt.");
        }
        UserSummary courier = userClient.getActiveCourier(request.livreurId());
        if (!"ROLE_LIVREUR".equals(courier.role()) || !courier.active()) {
            throw new IllegalArgumentException("The selected user is not an active internal courier");
        }
        livraison.setLivreurId(courier.id());
        livraison.mettreAJourStatut(StatutLivraison.EN_PREPARATION);
        return livraisonMapper.toResponse(livraisonRepository.save(livraison));
    }

    private Livraison findDelivery(Long id) {
        Livraison delivery = livraisonRepository.findByIdLivraisonAndEnterpriseId(
                        id, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found with ID: " + id));
        ensureCourierAccess(delivery);
        return delivery;
    }

    private boolean isAllowedTransition(Livraison delivery, StatutLivraison next) {
        StatutLivraison current = delivery.getStatutLivraison();
        if (current == next) return false;
        return switch (current) {
            case EN_PREPARATION -> next == StatutLivraison.ECHEC
                    || (delivery.getTypeTransporteur() == TypeTransporteur.LIVREUR_INTERNE
                    ? next == StatutLivraison.EN_COURS
                    : next == StatutLivraison.CHEZ_TRANSPORTEUR);
            case CHEZ_TRANSPORTEUR -> next == StatutLivraison.EN_COURS
                    || next == StatutLivraison.ECHEC || next == StatutLivraison.RETOUR;
            case EN_COURS -> next == StatutLivraison.LIVREE
                    || next == StatutLivraison.ECHEC || next == StatutLivraison.RETOUR;
            case ECHEC -> next == StatutLivraison.EN_COURS || next == StatutLivraison.RETOUR;
            case LIVREE, RETOUR -> false;
        };
    }

    private void validateTransporter(ExpedierLivraisonRequest request) {
        if (request.getTypeTransporteur() == TypeTransporteur.LIVREUR_INTERNE) {
            if (request.getLivreurId() == null) {
                throw new IllegalArgumentException("An internal courier must be selected");
            }
            if (hasText(request.getNomSociete())) {
                throw new IllegalArgumentException("External carrier fields cannot be used for an internal courier");
            }
            UserSummary courier = userClient.getActiveCourier(request.getLivreurId());
            if (!"ROLE_LIVREUR".equals(courier.role()) || !courier.active()) {
                throw new IllegalArgumentException("The selected user is not an active internal courier");
            }
            return;
        }

        if (!hasText(request.getNomSociete())) {
            throw new IllegalArgumentException("External delivery company name is required");
        }
        if (request.getLivreurId() != null) {
            throw new IllegalArgumentException("An external-company shipment cannot be assigned to an internal courier");
        }
    }

    private OrderClient.OrderSummary requireShippableOrder(Long orderId) {
        OrderClient.OrderSummary order;
        try {
            order = orderClient.getOrder(orderId);
        } catch (FeignException exception) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        if (order == null || !"PREPARATION".equals(order.statutCommande())) {
            throw new IllegalStateException("Only an order in preparation can be shipped.");
        }
        if (!"PAID".equals(order.statutPaiement())
                && !"AWAITING_COLLECTION".equals(order.statutPaiement())) {
            throw new IllegalStateException(
                    "The order must be paid or explicitly configured for cash on delivery before shipping.");
        }
        OrderClient.CustomerSummary customer = order.infosClient();
        if (customer == null || !hasText(customer.nomComplet()) || !hasText(customer.telephone())
                || !hasText(customer.adresseLivraison()) || !hasText(customer.ville())) {
            throw new IllegalStateException("The order has incomplete delivery details.");
        }
        return order;
    }

    private void completeOrderDelivery(Livraison delivery) {
        if (delivery.getMontantACollecterCoD() > 0) {
            paymentClient.collectCashOnDelivery(delivery.getReferenceCommandeId());
        }
        orderClient.updateFulfillmentStatus(delivery.getReferenceCommandeId(),
                new OrderClient.StatusUpdate("LIVREE"));
    }

    private void ensureCourierAccess(Livraison delivery) {
        Long courierId = currentCourierId();
        if (courierId != null && !courierId.equals(delivery.getLivreurId())) {
            throw new AccessDeniedException("Couriers can access only deliveries assigned to them");
        }
    }

    private void ensureStatusOwnership(Livraison delivery) {
        boolean courier = hasRole("ROLE_LIVREUR");
        if (delivery.getTypeTransporteur() == TypeTransporteur.LIVREUR_INTERNE && !courier) {
            throw new AccessDeniedException("The assigned courier controls an internal delivery's execution status.");
        }
        if (delivery.getTypeTransporteur() == TypeTransporteur.SOCIETE_LIVRAISON && courier) {
            throw new AccessDeniedException("External-carrier deliveries are managed by logistics.");
        }
    }

    private boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private Long currentCourierId() {
        boolean courier = hasRole("ROLE_LIVREUR");
        return courier ? TenantContext.requireUserId() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
