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
import org.example.delivery_service.dto.request.CompleteDeliveryRequest;
import org.example.delivery_service.dto.request.FailedDeliveryAttemptRequest;
import org.example.delivery_service.dto.response.CourierDashboardResponse;
import org.example.delivery_service.dto.response.ProofPhotoResponse;
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
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.example.common.dto.PageResponse;
import org.example.common.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import feign.FeignException;
import org.springframework.web.multipart.MultipartFile;

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
    private final DeliveryProofStorage proofStorage;

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
                .statutLivraison(request.getTypeTransporteur() == TypeTransporteur.LIVREUR_INTERNE
                        ? StatutLivraison.ASSIGNEE : StatutLivraison.EN_PREPARATION)
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
        ensureGenericStatusOwnership(livraison, request.getStatut());

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
        if (livraison.getTypeTransporteur() != TypeTransporteur.SOCIETE_LIVRAISON) {
            throw new AccessDeniedException("Internal deliveries require courier proof of delivery.");
        }

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
        if (livraison.getStatutLivraison() != StatutLivraison.ASSIGNEE
                && livraison.getStatutLivraison() != StatutLivraison.EN_PREPARATION
                && livraison.getStatutLivraison() != StatutLivraison.ECHEC) {
            throw new IllegalStateException("A courier can be reassigned only before dispatch or after a failed attempt.");
        }
        UserSummary courier = userClient.getActiveCourier(request.livreurId());
        if (!"ROLE_LIVREUR".equals(courier.role()) || !courier.active()) {
            throw new IllegalArgumentException("The selected user is not an active internal courier");
        }
        livraison.setLivreurId(courier.id());
        livraison.mettreAJourStatut(StatutLivraison.ASSIGNEE);
        livraison.setAcceptedAt(null);
        livraison.setStartedAt(null);
        return livraisonMapper.toResponse(livraisonRepository.save(livraison));
    }

    @Override
    @Transactional
    public LivraisonResponse acceptAssignment(Long id) {
        Livraison delivery = requireInternalCourierDelivery(id);
        if (delivery.getStatutLivraison() != StatutLivraison.ASSIGNEE
                && delivery.getStatutLivraison() != StatutLivraison.EN_PREPARATION) {
            throw new IllegalStateException("Only a newly assigned delivery can be accepted.");
        }
        delivery.setAcceptedAt(LocalDateTime.now());
        delivery.mettreAJourStatut(StatutLivraison.ACCEPTEE);
        return livraisonMapper.toResponse(livraisonRepository.save(delivery));
    }

    @Override
    @Transactional
    public LivraisonResponse startDelivery(Long id) {
        Livraison delivery = requireInternalCourierDelivery(id);
        if (delivery.getStatutLivraison() != StatutLivraison.ACCEPTEE
                && delivery.getStatutLivraison() != StatutLivraison.ECHEC) {
            throw new IllegalStateException("Accept the assignment before starting delivery.");
        }
        delivery.setStartedAt(LocalDateTime.now());
        delivery.setFailureReason(null);
        delivery.setFailureNote(null);
        delivery.mettreAJourStatut(StatutLivraison.EN_COURS);
        orderClient.updateFulfillmentStatus(delivery.getReferenceCommandeId(),
                new OrderClient.StatusUpdate("EXPEDIEE"));
        return livraisonMapper.toResponse(livraisonRepository.save(delivery));
    }

    @Override
    @Transactional
    public LivraisonResponse reportFailedAttempt(Long id, FailedDeliveryAttemptRequest request) {
        Livraison delivery = requireInternalCourierDelivery(id);
        if (!List.of(StatutLivraison.ASSIGNEE, StatutLivraison.ACCEPTEE, StatutLivraison.EN_COURS)
                .contains(delivery.getStatutLivraison())) {
            throw new IllegalStateException("A failed attempt can be recorded only on an active assignment.");
        }
        validateCoordinates(request.latitude(), request.longitude());
        delivery.setFailureReason(request.reason());
        delivery.setFailureNote(trimToNull(request.note()));
        delivery.setLastLatitude(request.latitude());
        delivery.setLastLongitude(request.longitude());
        delivery.setLastAttemptAt(LocalDateTime.now());
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.mettreAJourStatut(StatutLivraison.ECHEC);
        return livraisonMapper.toResponse(livraisonRepository.save(delivery));
    }

    @Override
    @Transactional
    public LivraisonResponse requestReturn(Long id) {
        Livraison delivery = requireInternalCourierDelivery(id);
        if (delivery.getStatutLivraison() != StatutLivraison.ECHEC) {
            throw new IllegalStateException("A return can be requested only after a failed attempt.");
        }
        delivery.setReturnRequestedAt(LocalDateTime.now());
        delivery.mettreAJourStatut(StatutLivraison.RETOUR_DEMANDE);
        return livraisonMapper.toResponse(livraisonRepository.save(delivery));
    }

    @Override
    @Transactional
    public LivraisonResponse completeDelivery(
            Long id, CompleteDeliveryRequest request, MultipartFile proofPhoto) {
        Livraison delivery = requireInternalCourierDelivery(id);
        if (delivery.getStatutLivraison() != StatutLivraison.EN_COURS) {
            throw new IllegalStateException("Only an in-progress delivery can be completed.");
        }
        validateCoordinates(request.latitude(), request.longitude());
        BigDecimal expectedCod = BigDecimal.valueOf(delivery.getMontantACollecterCoD())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal collectedCod = request.collectedCodAmount() == null
                ? BigDecimal.ZERO : request.collectedCodAmount().setScale(2, RoundingMode.HALF_UP);
        if (expectedCod.compareTo(collectedCod) != 0) {
            throw new IllegalArgumentException(
                    "Collected COD amount must equal the required amount. Report a payment problem instead.");
        }

        String proofObjectKey = null;
        if (proofPhoto != null && !proofPhoto.isEmpty()) {
            proofObjectKey = proofStorage.store(delivery.getEnterpriseId(), delivery.getIdLivraison(), proofPhoto);
        }
        delivery.setDeliveredTo(request.recipientName().trim());
        delivery.setProofSignature(request.signature().trim());
        delivery.setProofPhotoObjectKey(proofObjectKey);
        delivery.setProofCapturedAt(LocalDateTime.now());
        delivery.setLastLatitude(request.latitude());
        delivery.setLastLongitude(request.longitude());
        delivery.setCodCollectedAmount(collectedCod);
        delivery.setCodDiscrepancyNote(trimToNull(request.codDiscrepancyNote()));
        completeOrderDelivery(delivery);
        delivery.mettreAJourStatut(StatutLivraison.LIVREE);
        Livraison saved = livraisonRepository.save(delivery);
        notifyDelivered(saved);
        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LivraisonResponse reconcileCod(Long id) {
        Livraison delivery = findDelivery(id);
        if (delivery.getStatutLivraison() != StatutLivraison.LIVREE
                || delivery.getCodCollectedAmount() == null
                || delivery.getCodCollectedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Only collected COD from a delivered shipment can be reconciled.");
        }
        if (delivery.getCodReconciledAt() != null) {
            throw new IllegalStateException("COD has already been reconciled.");
        }
        delivery.setCodReconciledAt(LocalDateTime.now());
        delivery.setCodReconciledBy(TenantContext.requireUserId());
        return livraisonMapper.toResponse(livraisonRepository.save(delivery));
    }

    @Override
    @Transactional(readOnly = true)
    public CourierDashboardResponse courierDashboard() {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        Long courierId = TenantContext.requireUserId();
        LocalDateTime today = LocalDate.now().atStartOfDay();
        List<StatutLivraison> active = List.of(
                StatutLivraison.ASSIGNEE, StatutLivraison.ACCEPTEE,
                StatutLivraison.EN_PREPARATION, StatutLivraison.EN_COURS);
        return new CourierDashboardResponse(
                livraisonRepository.countByEnterpriseIdAndLivreurIdAndShippingDateGreaterThanEqual(
                        enterpriseId, courierId, today),
                livraisonRepository.countByEnterpriseIdAndLivreurIdAndStatutLivraisonIn(
                        enterpriseId, courierId, active),
                livraisonRepository.countByEnterpriseIdAndLivreurIdAndDeliveryDateGreaterThanEqual(
                        enterpriseId, courierId, today),
                livraisonRepository.countByEnterpriseIdAndLivreurIdAndStatutLivraison(
                        enterpriseId, courierId, StatutLivraison.ECHEC),
                livraisonRepository.sumUnreconciledCod(enterpriseId, courierId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProofPhotoResponse getProofPhoto(Long id) {
        Livraison delivery = findDelivery(id);
        if (!hasText(delivery.getProofPhotoObjectKey())) {
            throw new ResourceNotFoundException("No proof photo is available for this delivery.");
        }
        String contentType = delivery.getProofPhotoObjectKey().endsWith(".png")
                ? "image/png" : "image/jpeg";
        return new ProofPhotoResponse(
                proofStorage.load(delivery.getProofPhotoObjectKey()), contentType);
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
            case ASSIGNEE -> next == StatutLivraison.ACCEPTEE || next == StatutLivraison.ECHEC;
            case ACCEPTEE -> next == StatutLivraison.EN_COURS || next == StatutLivraison.ECHEC;
            case EN_PREPARATION -> next == StatutLivraison.ECHEC
                    || (delivery.getTypeTransporteur() == TypeTransporteur.LIVREUR_INTERNE
                    ? next == StatutLivraison.EN_COURS
                    : next == StatutLivraison.CHEZ_TRANSPORTEUR);
            case CHEZ_TRANSPORTEUR -> next == StatutLivraison.EN_COURS
                    || next == StatutLivraison.ECHEC || next == StatutLivraison.RETOUR;
            case EN_COURS -> next == StatutLivraison.LIVREE
                    || next == StatutLivraison.ECHEC || next == StatutLivraison.RETOUR;
            case ECHEC -> next == StatutLivraison.EN_COURS || next == StatutLivraison.RETOUR;
            case RETOUR_DEMANDE -> next == StatutLivraison.RETOUR;
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

    private void ensureGenericStatusOwnership(Livraison delivery, StatutLivraison next) {
        boolean courier = hasRole("ROLE_LIVREUR");
        if (delivery.getTypeTransporteur() == TypeTransporteur.LIVREUR_INTERNE) {
            if (courier) {
                throw new AccessDeniedException("Use the courier workflow actions to update an internal delivery.");
            }
            if (delivery.getStatutLivraison() != StatutLivraison.RETOUR_DEMANDE
                    || next != StatutLivraison.RETOUR) {
                throw new AccessDeniedException("Logistics can only approve a courier's return request.");
            }
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

    private Livraison requireInternalCourierDelivery(Long id) {
        Livraison delivery = findDelivery(id);
        if (!hasRole("ROLE_LIVREUR") || delivery.getTypeTransporteur() != TypeTransporteur.LIVREUR_INTERNE) {
            throw new AccessDeniedException("This action belongs to the assigned internal courier.");
        }
        return delivery;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together.");
        }
        if (latitude != null && (latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("Invalid GPS coordinates.");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notifyDelivered(Livraison delivery) {
        if (hasText(delivery.getClientEmail())) {
            eventProducer.sendNotificationEvent(
                    delivery.getClientEmail(),
                    "Commande livree",
                    "Votre commande #" + delivery.getReferenceCommandeId() + " a bien ete livree. Merci !");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
