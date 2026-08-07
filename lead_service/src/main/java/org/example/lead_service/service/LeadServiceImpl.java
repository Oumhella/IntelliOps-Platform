package org.example.lead_service.service;

import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.PageResponse;
import org.example.common.exception.ConflictException;
import org.example.common.exception.PaymentRequiredException;
import org.example.common.exception.ResourceNotFoundException;
import org.example.common.security.TenantContext;
import org.example.lead_service.client.AbonnementClient;
import org.example.lead_service.client.StockClient;
import org.example.lead_service.client.UserClient;
import org.example.lead_service.dto.*;
import org.example.lead_service.entity.*;
import org.example.lead_service.event.OrderEventProducer;
import org.example.lead_service.mapper.CommandeMapper;
import org.example.lead_service.mapper.LeadMapper;
import org.example.lead_service.repository.CommandeRepository;
import org.example.lead_service.repository.LeadRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LeadServiceImpl implements LeadService {

    private final LeadRepository leadRepository;
    private final CommandeRepository commandeRepository;
    private final LeadMapper leadMapper;
    private final CommandeMapper commandeMapper;
    private final StockClient stockClient;
    private final AbonnementClient abonnementClient;
    private final UserClient userClient;
    private final OrderEventProducer orderEventProducer;

    @Override
    public LeadDTO creerLead(LeadDTO leadDTO) {
        Lead lead = leadMapper.toEntity(leadDTO);
        lead.setIdLead(null);
        lead.setEnterpriseId(TenantContext.requireEnterpriseId());
        lead.setAgentId(TenantContext.requireUserId());
        lead.setStatutLead(StatutLead.NEW_LEAD);
        lead.setSource(LeadSource.MANUAL);
        lead.setBoutiqueId(null);
        lead.setCommande(null);
        lead.setHistoriqueInteractions(new ArrayList<>());
        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Override
    @Transactional(readOnly = true)
    public LeadDTO obtenirLeadParId(Long idLead) {
        return leadMapper.toDto(findLead(idLead));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadDTO> obtenirLeadsParAgent(Long agentId) {
        return leadRepository.findByAgentIdAndEnterpriseId(agentId, TenantContext.requireEnterpriseId()).stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeadDTO> rechercherLeads(StatutLead statut, Long agentId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "idLead"));
        return PageResponse.from(
                leadRepository.search(TenantContext.requireEnterpriseId(), statut, agentId, pageable),
                leadMapper::toDto);
    }

    @Override
    public LeadDTO assignerAgent(Long idLead, Long agentId) {
        Lead lead = findLead(idLead);
        UserClient.UserSummary agent;
        try {
            agent = userClient.getStaffMember(agentId);
        } catch (FeignException exception) {
            throw new ResourceNotFoundException("The selected staff member does not exist in this enterprise.");
        }
        if (agent == null || !agent.active() || !"ROLE_CSM".equals(agent.role())) {
            throw new IllegalArgumentException("A lead can only be assigned to an active CSM in this enterprise.");
        }
        if (lead.getStatutLead() == StatutLead.CONVERTED || lead.getStatutLead() == StatutLead.REFUSED) {
            throw new IllegalStateException("A terminal lead cannot be reassigned.");
        }
        lead.assignerAgent(agentId);
        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Override
    public NoteInteractionDTO enregistrerInteraction(
            Long idLead, TypeInteraction type, String commentaire, StatutLead nouveauStatut) {
        Lead lead = findLead(idLead);
        assertAssignedToCurrentUser(lead);
        if (type == null || commentaire == null || commentaire.isBlank()) {
            throw new IllegalArgumentException("An interaction type and comment are required.");
        }

        String ancienStatut = lead.getStatutLead().name();
        if (nouveauStatut != null) {
            lead.changerStatut(nouveauStatut);
        }

        NoteInteraction note = NoteInteraction.builder()
                .lead(lead)
                .ancienStatut(ancienStatut)
                .nouveauStatut(lead.getStatutLead().name())
                .typeInteraction(type)
                .commentaireAgent(commentaire.trim())
                .build();
        lead.getHistoriqueInteractions().add(note);
        Lead savedLead = leadRepository.save(lead);
        NoteInteraction savedNote = savedLead.getHistoriqueInteractions().stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("The interaction could not be persisted."));
        return leadMapper.toDto(savedNote);
    }

    @Override
    public CommandeDTO convertirEnCommande(Long idLead, CreationCommandeRequest request) {
        Lead lead = findLead(idLead);
        assertAssignedToCurrentUser(lead);
        requireDeliveryDetails(lead);
        assertMonthlyOrderAllowance();

        String orderReference = deterministicReference(request.getIdempotencyKey());
        var existing = commandeRepository.findByReferenceAndLeadEnterpriseId(
                orderReference, TenantContext.requireEnterpriseId());
        if (existing.isPresent()) {
            if (!existing.get().getLead().getIdLead().equals(idLead)) {
                throw new IllegalStateException("This idempotency key belongs to another lead conversion.");
            }
            return commandeMapper.toDto(existing.get());
        }

        List<PreparedItem> items = prepareItems(request);
        List<PreparedItem> reserved = new ArrayList<>();
        try {
            for (PreparedItem item : items) {
                stockClient.reserverStock(request.getStockLocationId(), item.productId(),
                        new StockClient.ReservationRequest(item.quantity(), orderReference));
                reserved.add(item);
            }

            Commande order = lead.convertirEnCommande(request.getStockLocationId(), orderReference);
            items.forEach(item -> order.ajouterLigne(item.productId(), item.quantity(), item.unitPrice()));
            Lead savedLead = leadRepository.save(lead);
            notifyOrderCreated(savedLead);
            return commandeMapper.toDto(savedLead.getCommande());
        } catch (RuntimeException exception) {
            releaseReservations(request.getStockLocationId(), orderReference, reserved);
            throw exception;
        }
    }

    @Override
    public CommandeDTO importExternalOrder(ExternalOrderImportRequest request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        LeadSource source;
        StatutPaiementCommande paymentStatus;
        try {
            source = LeadSource.valueOf(request.platform().trim().toUpperCase(Locale.ROOT));
            paymentStatus = StatutPaiementCommande.valueOf(request.paymentStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported external platform or payment status.");
        }
        if (source != LeadSource.SHOPIFY && source != LeadSource.WOOCOMMERCE) {
            throw new IllegalArgumentException("Only Shopify and WooCommerce orders can use this import boundary.");
        }
        if (paymentStatus != StatutPaiementCommande.UNPAID
                && paymentStatus != StatutPaiementCommande.AWAITING_COLLECTION
                && paymentStatus != StatutPaiementCommande.PAID) {
            throw new IllegalArgumentException("The imported payment status is not valid for a new order.");
        }

        String orderReference = externalReference(enterpriseId, source, request.externalOrderId());
        var existing = commandeRepository.findByReferenceAndLeadEnterpriseId(orderReference, enterpriseId);
        if (existing.isPresent()) return commandeMapper.toDto(existing.get());
        assertMonthlyOrderAllowance();

        try { stockClient.obtenirBoutique(request.stockLocationId()); }
        catch (FeignException exception) { throw new ResourceNotFoundException("The mapped fulfillment location does not exist."); }

        List<PreparedItem> prepared = new ArrayList<>();
        Set<Long> products = new HashSet<>();
        for (ExternalOrderImportRequest.Line line : request.items()) {
            if (!products.add(line.productId())) throw new IllegalArgumentException("An imported order may reference each internal product only once.");
            try {
                StockProductDTO product = stockClient.obtenirProduit(line.productId());
                StockInventoryDTO inventory = stockClient.obtenirInventaire(request.stockLocationId(), line.productId());
                if (product == null || inventory == null || inventory.getQuantiteDisponible() < line.quantity()) {
                    throw new IllegalStateException("Insufficient mapped stock for product " + line.productId() + ".");
                }
            } catch (FeignException exception) {
                throw new ResourceNotFoundException("Mapped product or inventory not found for product " + line.productId() + ".");
            }
            prepared.add(new PreparedItem(line.productId(), line.quantity(), line.unitPrice().doubleValue()));
        }

        List<PreparedItem> reserved = new ArrayList<>();
        try {
            for (PreparedItem item : prepared) {
                stockClient.reserverStock(request.stockLocationId(), item.productId(),
                        new StockClient.ReservationRequest(item.quantity(), orderReference));
                reserved.add(item);
            }
            ExternalOrderImportRequest.Customer externalCustomer = request.customer();
            CoordonneesClient customer = CoordonneesClient.builder().nomComplet(externalCustomer.fullName().trim())
                    .email(blankToNull(externalCustomer.email())).telephone(blankToNull(externalCustomer.phone()))
                    .adresseLivraison(externalCustomer.address().trim()).ville(externalCustomer.city().trim()).build();
            Lead lead = Lead.builder().statutLead(StatutLead.CONVERTED).ordrePriorite(OrdrePriorite.MEDIUM)
                    .infosClient(customer).boutiqueId(request.stockLocationId()).agentId(null).source(source)
                    .enterpriseId(enterpriseId).historiqueInteractions(new ArrayList<>()).build();
            Commande order = Commande.builder().lead(lead).reference(orderReference).statutCommande(StatutCommande.CONFIRMEE)
                    .statutPaiement(paymentStatus).infosClient(customer).stockLocationId(request.stockLocationId())
                    .stockReservationReference(orderReference).totalPrix(0.0).build();
            prepared.forEach(item -> order.ajouterLigne(item.productId(), item.quantity(), item.unitPrice()));
            order.setTotalPrix(request.totalAmount().doubleValue());
            lead.setCommande(order);
            Lead saved = leadRepository.save(lead);
            notifyOrderCreated(saved);
            return commandeMapper.toDto(saved.getCommande());
        } catch (RuntimeException exception) {
            releaseReservations(request.stockLocationId(), orderReference, reserved);
            throw exception;
        }
    }

    @Override
    public CommandeDTO syncExternalOrderState(ExternalOrderStateRequest request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        LeadSource source;
        StatutPaiementCommande desiredPayment;
        try {
            source = LeadSource.valueOf(request.platform().trim().toUpperCase(Locale.ROOT));
            desiredPayment = StatutPaiementCommande.valueOf(request.paymentStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported external order state.");
        }
        if (source != LeadSource.SHOPIFY && source != LeadSource.WOOCOMMERCE) {
            throw new IllegalArgumentException("Only Shopify and WooCommerce orders can use this state boundary.");
        }
        String reference = externalReference(enterpriseId, source, request.externalOrderId());
        Commande order = commandeRepository.findByReferenceAndLeadEnterpriseId(reference, enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException("Imported external order not found."));

        if (!request.cancelled()) assertExternalOrderUnchanged(order, request);
        applyExternalPaymentState(order, desiredPayment);
        if (request.cancelled() && order.getStatutCommande() != StatutCommande.ANNULEE
                && order.getStatutCommande() != StatutCommande.RETOURNEE) {
            if (order.getStatutCommande() == StatutCommande.EXPEDIEE || order.getStatutCommande() == StatutCommande.LIVREE) {
                order.setStatutCommande(StatutCommande.RETOURNEE);
            } else {
                List<PreparedItem> items = order.getLignesCommande().stream()
                        .map(line -> new PreparedItem(line.getProduitId(), line.getQuantite(), line.getPrixUnitaireApplique()))
                        .toList();
                releaseReservations(order.getStockLocationId(), order.getStockReservationReference(), items);
                order.setStatutCommande(StatutCommande.ANNULEE);
            }
        }
        return commandeMapper.toDto(commandeRepository.save(order));
    }

    private void assertExternalOrderUnchanged(Commande order, ExternalOrderStateRequest request) {
        if (BigDecimal.valueOf(order.getTotalPrix()).compareTo(request.totalAmount()) != 0) {
            throw new ConflictException("External order total changed; review and reconcile this order before continuing.");
        }
        Map<Long, ExternalOrderImportRequest.Line> requested = request.items().stream()
                .collect(Collectors.toMap(ExternalOrderImportRequest.Line::productId, line -> line,
                        (first, duplicate) -> { throw new IllegalArgumentException("An external update contains duplicate mapped products."); }));
        if (requested.size() != order.getLignesCommande().size()) {
            throw new ConflictException("External order items changed; review and reconcile this order before continuing.");
        }
        boolean changed = order.getLignesCommande().stream().anyMatch(line -> {
            ExternalOrderImportRequest.Line candidate = requested.get(line.getProduitId());
            return candidate == null || candidate.quantity() != line.getQuantite()
                    || BigDecimal.valueOf(line.getPrixUnitaireApplique()).compareTo(candidate.unitPrice()) != 0;
        });
        if (changed) throw new ConflictException("External order items changed; review and reconcile this order before continuing.");
    }

    private void applyExternalPaymentState(Commande order, StatutPaiementCommande desired) {
        StatutPaiementCommande current = order.getStatutPaiement();
        if (current == desired) return;
        boolean allowed = switch (current) {
            case UNPAID -> desired == StatutPaiementCommande.AWAITING_COLLECTION
                    || desired == StatutPaiementCommande.PAID
                    || desired == StatutPaiementCommande.PARTIALLY_REFUNDED
                    || desired == StatutPaiementCommande.REFUNDED;
            case AWAITING_COLLECTION -> desired == StatutPaiementCommande.UNPAID
                    || desired == StatutPaiementCommande.PAID
                    || desired == StatutPaiementCommande.PARTIALLY_REFUNDED
                    || desired == StatutPaiementCommande.REFUNDED;
            case PAID -> desired == StatutPaiementCommande.PARTIALLY_REFUNDED || desired == StatutPaiementCommande.REFUNDED;
            case PARTIALLY_REFUNDED -> desired == StatutPaiementCommande.REFUNDED;
            case REFUNDED -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid provider payment transition from " + current + " to " + desired + ".");
        order.setStatutPaiement(desired);
    }

    private List<PreparedItem> prepareItems(CreationCommandeRequest request) {
        try {
            stockClient.obtenirBoutique(request.getStockLocationId());
        } catch (FeignException exception) {
            throw new ResourceNotFoundException("The selected fulfillment location does not exist.");
        }

        Set<Long> productIds = new HashSet<>();
        List<PreparedItem> prepared = new ArrayList<>();
        for (CreationCommandeRequest.ItemRequest item : request.getItems()) {
            if (!productIds.add(item.getProductId())) {
                throw new IllegalArgumentException("Each product may appear only once in an order.");
            }
            try {
                StockProductDTO product = stockClient.obtenirProduit(item.getProductId());
                StockInventoryDTO inventory = stockClient.obtenirInventaire(
                        request.getStockLocationId(), item.getProductId());
                if (product == null || product.getPrixVente() <= 0) {
                    throw new IllegalStateException("The product has no valid sale price.");
                }
                if (inventory == null || inventory.getQuantiteDisponible() < item.getQuantity()) {
                    throw new IllegalStateException(
                            "Insufficient available stock for product " + item.getProductId() + ".");
                }
                prepared.add(new PreparedItem(
                        item.getProductId(), item.getQuantity(), product.getPrixVente()));
            } catch (FeignException exception) {
                throw new ResourceNotFoundException(
                        "Product or inventory not found for product " + item.getProductId() + ".");
            }
        }
        return prepared;
    }

    private void assertMonthlyOrderAllowance() {
        AbonnementClient.Entitlement entitlement;
        try {
            entitlement = abonnementClient.currentEntitlement();
        } catch (FeignException exception) {
            throw new IllegalStateException("The subscription entitlement could not be verified.");
        }
        if (entitlement == null || !entitlement.active()) {
            throw new PaymentRequiredException(entitlement == null || entitlement.reason() == null
                    ? "An active subscription is required."
                    : entitlement.reason());
        }
        int limit = entitlement.monthlyOrderLimit();
        if (limit > 0) {
            LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
            LocalDateTime from = firstDay.atStartOfDay();
            LocalDateTime until = firstDay.plusMonths(1).atStartOfDay();
            long used = commandeRepository
                    .countByLeadEnterpriseIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            TenantContext.requireEnterpriseId(), from, until);
            if (used >= limit) {
                throw new IllegalStateException(
                        "The monthly order limit for the current subscription has been reached.");
            }
        }
    }

    private String deterministicReference(String idempotencyKey) {
        String scope = TenantContext.requireEnterpriseId() + ":" + idempotencyKey.trim();
        UUID id = UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8));
        return "CMD-" + id.toString().substring(0, 13).toUpperCase(Locale.ROOT);
    }

    private String externalReference(Long enterpriseId, LeadSource source, String externalOrderId) {
        String scope = enterpriseId + ":" + source + ":" + externalOrderId.trim();
        UUID id = UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8));
        return "EXT-" + id.toString().substring(0, 13).toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void releaseReservations(Long locationId, String reference, List<PreparedItem> items) {
        for (PreparedItem item : items) {
            try {
                stockClient.libererStock(locationId, item.productId(),
                        new StockClient.ReservationRequest(item.quantity(), reference));
            } catch (RuntimeException releaseFailure) {
                log.error("Stock reservation {} for product {} needs reconciliation: {}",
                        reference, item.productId(), releaseFailure.getMessage());
            }
        }
    }

    private void notifyOrderCreated(Lead savedLead) {
        if (savedLead.getInfosClient() == null || savedLead.getInfosClient().getEmail() == null
                || savedLead.getInfosClient().getEmail().isBlank()) {
            return;
        }
        try {
            orderEventProducer.sendOrderNotification(
                    savedLead.getInfosClient().getEmail(),
                    "Confirmation de votre commande " + savedLead.getCommande().getReference(),
                    "Votre commande " + savedLead.getCommande().getReference()
                            + " a ete creee pour un montant total de "
                            + savedLead.getCommande().getTotalPrix() + " MAD.");
        } catch (RuntimeException notificationFailure) {
            log.warn("Order {} was created but its notification could not be queued: {}",
                    savedLead.getCommande().getReference(), notificationFailure.getMessage());
        }
    }

    private void requireDeliveryDetails(Lead lead) {
        CoordonneesClient customer = lead.getInfosClient();
        if (customer == null || isBlank(customer.getNomComplet()) || isBlank(customer.getTelephone())
                || isBlank(customer.getAdresseLivraison()) || isBlank(customer.getVille())) {
            throw new IllegalStateException(
                    "Customer name, phone, delivery address, and city are required before conversion.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Lead findLead(Long idLead) {
        return leadRepository.findByIdLeadAndEnterpriseId(idLead, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Lead not found: " + idLead));
    }

    private void assertAssignedToCurrentUser(Lead lead) {
        if (!TenantContext.requireUserId().equals(lead.getAgentId())) {
            throw new AccessDeniedException("This lead is assigned to another CSM user.");
        }
    }

    private record PreparedItem(Long productId, int quantity, double unitPrice) {
    }
}
