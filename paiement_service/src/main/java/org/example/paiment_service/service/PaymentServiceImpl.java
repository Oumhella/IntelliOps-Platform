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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.paiment_service.service.InvoicePdfService;
import jakarta.persistence.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TransactionPaiementRepository transactionRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayFactory gatewayFactory;
    private final InvoicePdfService invoicePdfService; // Injecté via constructeur
    @Override
    @Transactional
    public TransactionPaiementResponseDTO initierPaiement(InitierPaiementRequestDTO request) {
        // 1. Contrôle d'Idempotence (Anti-double débit)
        Optional<TransactionPaiement> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return paymentMapper.toResponse(existing.get()); // Renvoie directement le résultat précédent sans relancer la transaction
        }

        TransactionPaiement transaction = paymentMapper.toEntity(request);
        transaction.setIdempotencyKey(request.getIdempotencyKey());

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
        return paymentMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public TransactionPaiementResponseDTO rembourserPaiement(Long idTransaction, RemboursementRequestDTO request) {
        TransactionPaiement transaction = transactionRepository.findById(idTransaction)
                .orElseThrow(() -> new EntityNotFoundException("Transaction introuvable : " + idTransaction));

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
        TransactionPaiement transaction = transactionRepository.findById(idTransaction)
                .orElseThrow(() -> new EntityNotFoundException("Transaction introuvable"));

        transaction.annuler();
        return paymentMapper.toResponse(transactionRepository.save(transaction));
    }
}