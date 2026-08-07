package org.example.paiment_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.dto.PageResponse;
import org.example.common.exception.PaymentRequiredException;
import org.example.common.security.TenantContext;
import org.example.paiment_service.dto.request.ConsumePaymentRequest;
import org.example.paiment_service.dto.request.InitierPaiementRequestDTO;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.request.PreparePaymentRequestDTO;
import org.example.paiment_service.dto.request.OrderPaymentRequest;
import org.example.paiment_service.dto.response.FactureResponseDTO;
import org.example.paiment_service.dto.response.PaymentPreparationResponseDTO;
import org.example.paiment_service.dto.response.TransactionPaiementResponseDTO;
import org.example.paiment_service.entity.*;
import org.example.paiment_service.event.PaymentEventProducer;
import org.example.paiment_service.gateway.PaymentGatewayFactory;
import org.example.paiment_service.gateway.PaymentGatewayProvider;
import org.example.paiment_service.mapper.PaymentMapper;
import org.example.paiment_service.repository.FactureRepository;
import org.example.paiment_service.repository.TransactionPaiementRepository;
import org.example.paiment_service.client.OrderClient;
import feign.FeignException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final TransactionPaiementRepository transactionRepository;
    private final FactureRepository factureRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayFactory gatewayFactory;
    private final InvoicePdfService invoicePdfService;
    private final PaymentEventProducer paymentEventProducer;
    private final OrderClient orderClient;

    @Value("${stripe.publishable-key:}")
    private String stripePublishableKey;

    @Value("${stripe.currency:mad}")
    private String stripeCurrency;

    @Override
    @Transactional
    public TransactionPaiementResponseDTO initierPaiement(InitierPaiementRequestDTO request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        normalizeAndValidate(request);

        Optional<TransactionPaiement> existing = transactionRepository.findByIdempotencyKeyAndEnterpriseId(
                request.getIdempotencyKey().trim(), enterpriseId);
        if (existing.isPresent()) {
            assertSameLogicalPayment(existing.get(), request);
            return paymentMapper.toResponse(existing.get());
        }

        TransactionPaiement transaction = paymentMapper.toEntity(request);
        transaction.setIdempotencyKey(request.getIdempotencyKey().trim());
        transaction.setEnterpriseId(enterpriseId);
        transaction.setMontant(request.getMontant().setScale(2, RoundingMode.HALF_UP));
        transaction.setMontantRembourse(BigDecimal.ZERO.setScale(2));
        transaction.setStatut(StatutPaiement.PENDING);
        transaction.setNotificationEmail(authenticatedEmail());

        // Flush the idempotency record before contacting the provider. Stripe also
        // receives the same key, protecting retries after network interruptions.
        transactionRepository.saveAndFlush(transaction);

        transaction.setStatut(StatutPaiement.AWAITING_COLLECTION);

        if (transaction.getStatut() == StatutPaiement.COMPLETED) {
            tryGenerateInvoice(transaction);
        }

        TransactionPaiement saved = transactionRepository.save(transaction);
        if (saved.getStatut() == StatutPaiement.COMPLETED) {
            paymentEventProducer.sendPaymentNotification(
                    saved.getNotificationEmail(),
                    "Payment #" + saved.getId() + " confirmed",
                    "Your payment of " + saved.getMontant() + " MAD was captured successfully.");
        }
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentPreparationResponseDTO prepareOrderCardPayment(
            Long orderId, OrderPaymentRequest request) {
        OrderClient.OrderSummary order = requirePayableOrder(orderId, false);
        PaymentPreparationResponseDTO prepared = prepareCardPayment(new PreparePaymentRequestDTO(
                request.idempotencyKey(), orderId, Contexte.COMMANDE_PRODUCT, order.totalPrix()));
        assignOrderNotificationEmail(prepared.paymentId(), order);
        return prepared;
    }

    @Override
    @Transactional
    public TransactionPaiementResponseDTO initiateOrderCod(Long orderId, OrderPaymentRequest request) {
        OrderClient.OrderSummary order = requirePayableOrder(orderId, true);
        InitierPaiementRequestDTO payment = new InitierPaiementRequestDTO();
        payment.setIdempotencyKey(request.idempotencyKey());
        payment.setReferenceSourceId(orderId);
        payment.setTypeContexte(Contexte.COMMANDE_PRODUCT);
        payment.setMontant(order.totalPrix());
        payment.setMode(ModePaiement.CASH_ON_DELIVERY);
        TransactionPaiementResponseDTO response = initierPaiement(payment);
        assignOrderNotificationEmail(response.getId(), order);
        if ("UNPAID".equals(order.statutPaiement())) {
            syncOrderPaymentStatus(orderId, "AWAITING_COLLECTION");
        }
        return response;
    }

    @Override
    @Transactional
    public TransactionPaiementResponseDTO collectOrderCod(Long orderId) {
        TransactionPaiement transaction = transactionRepository
                .findFirstByEnterpriseIdAndReferenceSourceIdAndTypeContexteAndModeOrderByIdDesc(
                        TenantContext.requireEnterpriseId(), orderId,
                        Contexte.COMMANDE_PRODUCT, ModePaiement.CASH_ON_DELIVERY)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No cash-on-delivery payment exists for order " + orderId + "."));
        if (transaction.getStatut() == StatutPaiement.COMPLETED) {
            return paymentMapper.toResponse(transaction);
        }
        if (transaction.getStatut() != StatutPaiement.AWAITING_COLLECTION) {
            throw new IllegalStateException("This cash-on-delivery payment cannot be collected.");
        }
        transaction.setStatut(StatutPaiement.COMPLETED);
        if (transaction.getFacture() == null) {
            tryGenerateInvoice(transaction);
        }
        syncOrderPaymentStatus(orderId, "PAID");
        TransactionPaiement saved = transactionRepository.save(transaction);
        paymentEventProducer.sendPaymentNotification(
                saved.getNotificationEmail(),
                "Cash payment #" + saved.getId() + " collected",
                "Cash on delivery of " + saved.getMontant() + " MAD was collected.");
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentPreparationResponseDTO prepareCardPayment(PreparePaymentRequestDTO request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        BigDecimal amount = money(request.montant());
        String key = request.idempotencyKey().trim();
        requireStripeBrowserConfiguration();

        TransactionPaiement transaction = transactionRepository
                .findByIdempotencyKeyAndEnterpriseId(key, enterpriseId)
                .map(existing -> {
                    assertSamePreparedPayment(existing, request, amount);
                    return existing;
                })
                .orElseGet(() -> transactionRepository.saveAndFlush(TransactionPaiement.builder()
                        .idempotencyKey(key)
                        .enterpriseId(enterpriseId)
                        .referenceSourceId(request.referenceSourceId())
                        .typeContexte(request.typeContexte())
                        .montant(amount)
                        .montantRembourse(BigDecimal.ZERO.setScale(2))
                        .mode(ModePaiement.CREDIT_CARD)
                        .notificationEmail(authenticatedEmail())
                        .statut(StatutPaiement.PENDING)
                        .build()));

        PaymentGatewayProvider provider = gatewayFactory.getProvider(ModePaiement.CREDIT_CARD);
        PaymentGatewayProvider.PreparedPayment prepared;
        if (transaction.getProviderTransactionId() == null || transaction.getProviderTransactionId().isBlank()) {
            prepared = provider.preparerPaiement(amount, key);
            transaction.setProviderTransactionId(prepared.providerTransactionId());
        } else {
            prepared = provider.recupererPaiementPrepare(transaction.getProviderTransactionId());
        }
        assertProviderPaymentMatches(transaction, prepared.providerTransactionId(), prepared.amount(), prepared.currency());
        transaction.setStatut(prepared.status());
        transactionRepository.save(transaction);
        return new PaymentPreparationResponseDTO(transaction.getId(), prepared.clientSecret(),
                stripePublishableKey.trim(), transaction.getMontant(), stripeCurrency.toLowerCase(), transaction.getStatut());
    }

    @Override
    @Transactional
    public TransactionPaiementResponseDTO finalizeCardPayment(Long idTransaction) {
        TransactionPaiement transaction = transactionRepository.findForFinalization(
                        idTransaction, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + idTransaction));
        if (transaction.getMode() != ModePaiement.CREDIT_CARD
                || transaction.getProviderTransactionId() == null
                || transaction.getProviderTransactionId().isBlank()) {
            throw new IllegalStateException("This transaction has no Stripe PaymentIntent to verify.");
        }

        PaymentGatewayProvider.PaymentResult result = gatewayFactory.getProvider(ModePaiement.CREDIT_CARD)
                .verifierPaiement(transaction.getProviderTransactionId());
        assertProviderPaymentMatches(transaction, result.providerTransactionId(), result.amount(), result.currency());
        boolean newlyCompleted = transaction.getStatut() != StatutPaiement.COMPLETED
                && result.status() == StatutPaiement.COMPLETED;
        transaction.setStatut(result.status());
        if (newlyCompleted && transaction.getFacture() == null) {
            tryGenerateInvoice(transaction);
        }
        TransactionPaiement saved = transactionRepository.save(transaction);
        if (newlyCompleted) {
            if (saved.getTypeContexte() == Contexte.COMMANDE_PRODUCT) {
                syncOrderPaymentStatus(saved.getReferenceSourceId(), "PAID");
            }
            paymentEventProducer.sendPaymentNotification(
                    saved.getNotificationEmail(),
                    "Payment #" + saved.getId() + " confirmed",
                    "Your payment of " + saved.getMontant() + " MAD was captured successfully.");
        }
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TransactionPaiementResponseDTO consumeCompletedPayment(Long idTransaction, ConsumePaymentRequest request) {
        TransactionPaiement transaction = transactionRepository.findForConsumption(
                        idTransaction, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + idTransaction));

        if (transaction.getStatut() != StatutPaiement.COMPLETED) {
            throw new PaymentRequiredException("Payment must be completed before the subscription can be activated.");
        }
        if (transaction.getTypeContexte() != request.expectedContext()) {
            throw new IllegalArgumentException("Payment context does not match the requested operation.");
        }
        if (!transaction.getReferenceSourceId().equals(request.expectedSourceId())) {
            throw new IllegalArgumentException("Payment source does not match the selected plan.");
        }
        if (transaction.getMontant().compareTo(money(request.expectedAmount())) != 0) {
            throw new IllegalArgumentException("Captured amount does not match the authoritative plan price.");
        }
        transaction.markConsumed(request.consumptionReference().trim());
        return paymentMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public TransactionPaiementResponseDTO rembourserPaiement(Long idTransaction, RemboursementRequestDTO request) {
        TransactionPaiement transaction = findTransaction(idTransaction);
        if (transaction.getConsumedAt() != null) {
            throw new IllegalStateException("Cancel the linked subscription before refunding its consumed payment.");
        }
        BigDecimal amount = money(request.getMontant());
        if (transaction.getMode() == ModePaiement.CREDIT_CARD) {
            if (transaction.getProviderTransactionId() == null || transaction.getProviderTransactionId().isBlank()) {
                throw new IllegalStateException("The provider transaction ID is missing; this payment cannot be refunded safely.");
            }
            boolean refunded = gatewayFactory.getProvider(ModePaiement.CREDIT_CARD)
                    .traiterRemboursement(transaction.getProviderTransactionId(), amount,
                            "refund-" + transaction.getId() + "-" + transaction.getMontantRembourse().add(amount));
            if (!refunded) {
                throw new IllegalStateException("The payment provider did not confirm the refund.");
            }
        }
        transaction.rembourser(amount);
        TransactionPaiement saved = transactionRepository.save(transaction);
        if (saved.getTypeContexte() == Contexte.COMMANDE_PRODUCT) {
            syncOrderPaymentStatus(saved.getReferenceSourceId(),
                    saved.getStatut() == StatutPaiement.REFUNDED
                            ? "REFUNDED" : "PARTIALLY_REFUNDED");
        }
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TransactionPaiementResponseDTO annulerPaiement(Long idTransaction) {
        TransactionPaiement transaction = findTransaction(idTransaction);
        if (transaction.getConsumedAt() != null) {
            throw new IllegalStateException("A payment linked to a subscription cannot be cancelled directly.");
        }
        transaction.annuler();
        TransactionPaiement saved = transactionRepository.save(transaction);
        if (saved.getTypeContexte() == Contexte.COMMANDE_PRODUCT
                && saved.getMode() == ModePaiement.CASH_ON_DELIVERY) {
            syncOrderPaymentStatus(saved.getReferenceSourceId(), "UNPAID");
        }
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionPaiementResponseDTO getTransaction(Long idTransaction) {
        return paymentMapper.toResponse(findTransaction(idTransaction));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionPaiementResponseDTO> searchTransactions(
            StatutPaiement statut, Contexte contexte, int page, int size) {
        return PageResponse.from(
                transactionRepository.search(TenantContext.requireEnterpriseId(), statut, contexte,
                        pageRequest(page, size, "id")),
                paymentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FactureResponseDTO getInvoice(Long idFacture) {
        return paymentMapper.toResponse(findInvoice(idFacture));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FactureResponseDTO> getInvoices(int page, int size) {
        return PageResponse.from(
                factureRepository.findAllByTransactionPaiementEnterpriseId(
                        TenantContext.requireEnterpriseId(), pageRequest(page, size, "dateEmission")),
                paymentMapper::toResponse);
    }

    private void normalizeAndValidate(InitierPaiementRequestDTO request) {
        request.setMontant(money(request.getMontant()));
        if (request.getIdempotencyKey().trim().length() > 100) {
            throw new IllegalArgumentException("Idempotency key must not exceed 100 characters.");
        }
        if (request.getTypeContexte() == Contexte.ABONNEMENT_PLATFORM
                && request.getMode() != ModePaiement.CREDIT_CARD) {
            throw new IllegalArgumentException("Platform subscriptions require an immediately captured card payment.");
        }
        if (request.getMode() == ModePaiement.CREDIT_CARD) {
            throw new IllegalArgumentException(
                    "Card payments must use the secure /prepare and /{id}/finalize Payment Element flow.");
        }
    }

    private OrderClient.OrderSummary requirePayableOrder(Long orderId, boolean allowAwaitingCollection) {
        OrderClient.OrderSummary order;
        try {
            order = orderClient.getOrder(orderId);
        } catch (FeignException exception) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        }
        if (order == null || order.totalPrix() == null || order.totalPrix().signum() <= 0) {
            throw new IllegalStateException("The order has no payable total.");
        }
        if (!"CONFIRMEE".equals(order.statutCommande())) {
            throw new IllegalStateException("Only a confirmed order can enter payment.");
        }
        if (!"UNPAID".equals(order.statutPaiement())
                && !(allowAwaitingCollection && "AWAITING_COLLECTION".equals(order.statutPaiement()))) {
            throw new IllegalStateException("The order already has a payment workflow.");
        }
        return order;
    }

    private void syncOrderPaymentStatus(Long orderId, String desiredStatus) {
        OrderClient.OrderSummary order = orderClient.getOrder(orderId);
        if (order == null) {
            throw new IllegalStateException("The linked order could not be loaded.");
        }
        if (!desiredStatus.equals(order.statutPaiement())) {
            orderClient.updatePaymentStatus(orderId, new OrderClient.PaymentStatusUpdate(desiredStatus));
        }
    }

    private void assertSameLogicalPayment(TransactionPaiement existing, InitierPaiementRequestDTO request) {
        if (!existing.getReferenceSourceId().equals(request.getReferenceSourceId())
                || existing.getTypeContexte() != request.getTypeContexte()
                || existing.getMode() != request.getMode()
                || existing.getMontant().compareTo(request.getMontant()) != 0) {
            throw new IllegalArgumentException("The idempotency key is already assigned to a different payment.");
        }
    }

    private void assertSamePreparedPayment(
            TransactionPaiement existing, PreparePaymentRequestDTO request, BigDecimal amount) {
        if (!existing.getReferenceSourceId().equals(request.referenceSourceId())
                || existing.getTypeContexte() != request.typeContexte()
                || existing.getMode() != ModePaiement.CREDIT_CARD
                || existing.getMontant().compareTo(amount) != 0) {
            throw new IllegalArgumentException("The idempotency key is already assigned to a different payment.");
        }
        if (existing.getConsumedAt() != null || existing.getStatut() == StatutPaiement.REFUNDED
                || existing.getStatut() == StatutPaiement.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("This checkout can no longer be reused.");
        }
    }

    private void assertProviderPaymentMatches(
            TransactionPaiement transaction, String providerId, BigDecimal amount, String currency) {
        if (!transaction.getProviderTransactionId().equals(providerId)
                || transaction.getMontant().compareTo(amount) != 0
                || !stripeCurrency.equalsIgnoreCase(currency)) {
            throw new IllegalStateException("Stripe returned a PaymentIntent that does not match this transaction.");
        }
    }

    private void requireStripeBrowserConfiguration() {
        if (stripePublishableKey == null || !stripePublishableKey.startsWith("pk_")) {
            throw new IllegalStateException(
                    "Stripe publishable key is not configured. Set STRIPE_PUBLISHABLE_KEY before starting checkout.");
        }
    }

    private void tryGenerateInvoice(TransactionPaiement transaction) {
        Facture facture = Facture.builder()
                .numeroFactureUnique("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .dateEmission(LocalDateTime.now())
                .transactionPaiement(transaction)
                .build();
        try {
            facture.setCheminFichierPdf(invoicePdfService.genererEtStockerFacturePdf(facture, transaction));
            transaction.setFacture(facture);
        } catch (RuntimeException exception) {
            // A storage outage must not erase or retry an already captured charge.
            log.error("Invoice generation failed for payment {}", transaction.getId(), exception);
        }
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String authenticatedEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }

    private void assignOrderNotificationEmail(Long paymentId, OrderClient.OrderSummary order) {
        if (order.infosClient() == null || order.infosClient().email() == null
                || order.infosClient().email().isBlank()) {
            return;
        }
        transactionRepository.findByIdAndEnterpriseId(paymentId, TenantContext.requireEnterpriseId())
                .ifPresent(transaction -> {
                    transaction.setNotificationEmail(order.infosClient().email().trim());
                    transactionRepository.save(transaction);
                });
    }

    private TransactionPaiement findTransaction(Long idTransaction) {
        return transactionRepository.findByIdAndEnterpriseId(idTransaction, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + idTransaction));
    }

    private Facture findInvoice(Long idFacture) {
        return factureRepository.findByIdAndTransactionPaiementEnterpriseId(
                        idFacture, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + idFacture));
    }

    private PageRequest pageRequest(int page, int size, String sortProperty) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, sortProperty));
    }
}
