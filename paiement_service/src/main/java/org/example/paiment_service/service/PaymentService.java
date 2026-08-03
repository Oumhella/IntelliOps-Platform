package org.example.paiment_service.service;

import org.example.paiment_service.dto.request.InitierPaiementRequestDTO;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.response.TransactionPaiementResponseDTO;
import org.springframework.transaction.annotation.Transactional;
import org.example.common.dto.PageResponse;
import org.example.paiment_service.dto.response.FactureResponseDTO;
import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.StatutPaiement;

public interface PaymentService {
    @Transactional
    TransactionPaiementResponseDTO initierPaiement(InitierPaiementRequestDTO request);

    @Transactional
    TransactionPaiementResponseDTO rembourserPaiement(Long idTransaction, RemboursementRequestDTO request);

    @Transactional
    TransactionPaiementResponseDTO annulerPaiement(Long idTransaction);

    @Transactional(readOnly = true)
    TransactionPaiementResponseDTO getTransaction(Long idTransaction);

    @Transactional(readOnly = true)
    PageResponse<TransactionPaiementResponseDTO> searchTransactions(
            StatutPaiement statut, Contexte contexte, int page, int size);

    @Transactional(readOnly = true)
    FactureResponseDTO getInvoice(Long idFacture);

    @Transactional(readOnly = true)
    PageResponse<FactureResponseDTO> getInvoices(int page, int size);
}
