package org.example.paiment_service.service;

import lombok.RequiredArgsConstructor;
import org.example.paiment_service.dto.request.InitierPaiementRequestDTO;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.response.TransactionPaiementResponseDTO;
import org.example.paiment_service.entity.*;
import org.example.paiment_service.gateway.PaymentGatewayFactory;
import org.example.paiment_service.gateway.PaymentGatewayProvider;
import org.example.paiment_service.mapper.PaymentMapper;
import org.example.paiment_service.repository.TransactionPaiementRepository;
import org.example.paiment_service.repository.FactureRepository;
import org.example.paiment_service.dto.response.FactureResponseDTO;
import org.example.common.dto.PageResponse;
import org.example.common.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.paiment_service.service.InvoicePdfService;
import jakarta.persistence.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.example.paiment_service.event.PaymentEventProducer;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TransactionPaiementRepository transactionRepository;
    private final FactureRepository factureRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayFactory gatewayFactory;
    private final InvoicePdfService invoicePdfService;
    private final PaymentEventProducer paymentEventProducer;

    @Override
    @Transactional
    public TransactionPaiementResponseDTO initierPaiement(InitierPaiementRequestDTO request) {
        // 1. Contrôle d'Idempotence (Anti-double débit)
        Long enterpriseId = TenantContext.requireEnterpriseId();
        Optional<TransactionPaiement> existing = transactionRepository.findByIdempotencyKeyAndEnterpriseId(
                request.getIdempotencyKey(), enterpriseId);
        if (existing.isPresent()) {
            return paymentMapper.toResponse(existing.get()); // Renvoie directement le résultat précédent sans relancer la transaction
        }

        TransactionPaiement transaction = paymentMapper.toEntity(request);
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setEnterpriseId(enterpriseId);

        // 2. Traitement via Gateway de Paiement
        if (request.getMode() == ModePaiement.CREDIT_CARD) {
            ModeleTokenisation tokenisation = ModeleTokenisation.builder()
                    .systemAccountId(request.getSystemAccountId())
                    .tokenCarteSecurise(request.getTokenCarteSecurise())
                    .build();
            transaction.setTokenisation(tokenisation);

            PaymentGatewayProvider provider = gatewayFactory.getProvider(ModePaiement.CREDIT_CARD);
            boolean succes = provider.traiterPaiement(request.getMontant(), request.getTokenCarteSecurise());
            transaction.setStatut(succes ? StatutPaiement.COMPLETED : StatutPaiement.FAILED);
        } else {
            transaction.initierPaiement(); // Mode CASH_ON_DELIVERY -> AWAITING_COLLECTION
        }

        // 3. Génération de Facture si le paiement est valide
        if (transaction.getStatut() == StatutPaiement.COMPLETED || transaction.getStatut() == StatutPaiement.AWAITING_COLLECTION) {
            Facture facture = Facture.builder()
                    .numeroFactureUnique("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .dateEmission(LocalDateTime.now()) // <-- Make sure this is set here!
                    .transactionPaiement(transaction)
                    .build();

            // 1. Génération du PDF physique sur le disque
            String pathPdf = invoicePdfService.genererEtStockerFacturePdf(facture, transaction);
            facture.setCheminFichierPdf(pathPdf);

            transaction.setFacture(facture);
        }

        TransactionPaiement saved = transactionRepository.save(transaction);

        if (saved.getStatut() == StatutPaiement.COMPLETED) {
            paymentEventProducer.sendPaymentNotification(
                    "customer@example.com",
                    "Confirmation de votre paiement #" + saved.getId(),
                    "Votre paiement de " + saved.getMontant() + " MAD a été confirmé avec succès."
            );
        }

        return paymentMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public TransactionPaiementResponseDTO rembourserPaiement(Long idTransaction, RemboursementRequestDTO request) {
        TransactionPaiement transaction = findTransaction(idTransaction);

        if (transaction.getMode() == ModePaiement.CREDIT_CARD) {
            PaymentGatewayProvider provider = gatewayFactory.getProvider(ModePaiement.CREDIT_CARD);
            provider.traiterRemboursement(transaction.getTokenisation().getTokenCarteSecurise(), request.getMontant());
        }

        transaction.rembourser(request.getMontant());
        return paymentMapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    @Override
    public TransactionPaiementResponseDTO annulerPaiement(Long idTransaction) {
        TransactionPaiement transaction = findTransaction(idTransaction);

        transaction.annuler();
        return paymentMapper.toResponse(transactionRepository.save(transaction));
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
        PageRequest pageable = pageRequest(page, size, "id");
        return PageResponse.from(
                transactionRepository.search(TenantContext.requireEnterpriseId(), statut, contexte, pageable),
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

    private TransactionPaiement findTransaction(Long idTransaction) {
        return transactionRepository.findByIdAndEnterpriseId(
                        idTransaction, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Transaction introuvable : " + idTransaction));
    }

    private Facture findInvoice(Long idFacture) {
        return factureRepository.findByIdAndTransactionPaiementEnterpriseId(
                        idFacture, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new EntityNotFoundException("Facture introuvable : " + idFacture));
    }

    private PageRequest pageRequest(int page, int size, String sortProperty) {
        return PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, sortProperty));
    }
}
