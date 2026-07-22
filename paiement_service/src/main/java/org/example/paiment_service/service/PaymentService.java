package org.example.paiment_service.service;

import org.example.paiment_service.dto.request.InitierPaiementRequestDTO;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.response.TransactionPaiementResponseDTO;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentService {
    @Transactional
    TransactionPaiementResponseDTO initierPaiement(InitierPaiementRequestDTO request);

    @Transactional
    TransactionPaiementResponseDTO rembourserPaiement(Long idTransaction, RemboursementRequestDTO request);

    @Transactional
    TransactionPaiementResponseDTO annulerPaiement(Long idTransaction);
}
