package org.example.paiment_service.controller;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.example.paiment_service.dto.request.InitierPaiementRequestDTO;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.response.TransactionPaiementResponseDTO;
import org.example.paiment_service.entity.Facture;
import org.example.paiment_service.repository.FactureRepository;
import org.example.paiment_service.service.InvoicePdfService;
import org.example.paiment_service.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final InvoicePdfService invoicePdfService;
    private final FactureRepository factureRepository;

    @PostMapping("/initier")
    public ResponseEntity<TransactionPaiementResponseDTO> initierPaiement(@RequestBody InitierPaiementRequestDTO request) {
        return new ResponseEntity<>(paymentService.initierPaiement(request), HttpStatus.CREATED);
    }

    @PostMapping("/{idTransaction}/rembourser")
    public ResponseEntity<TransactionPaiementResponseDTO> rembourser(
            @PathVariable Long idTransaction,
            @RequestBody RemboursementRequestDTO request) {
        return ResponseEntity.ok(paymentService.rembourserPaiement(idTransaction, request));
    }

    @PostMapping("/{idTransaction}/annuler")
    public ResponseEntity<TransactionPaiementResponseDTO> annuler(@PathVariable Long idTransaction) {
        return ResponseEntity.ok(paymentService.annulerPaiement(idTransaction));
    }
    @GetMapping("/factures/{idFacture}/download-url")
    public ResponseEntity<String> TelechargerFactureUrl(@PathVariable Long idFacture) {
        Facture facture = factureRepository.findById(idFacture)
                .orElseThrow(() -> new EntityNotFoundException("Facture non trouvée"));

        String presignedUrl = invoicePdfService.obtenirUrlTelechargementTemporaire(facture.getCheminFichierPdf());
        return ResponseEntity.ok(presignedUrl);
    }
}