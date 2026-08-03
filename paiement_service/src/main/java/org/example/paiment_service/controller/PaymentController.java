package org.example.paiment_service.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.dto.PageResponse;
import org.example.paiment_service.dto.request.InitierPaiementRequestDTO;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.response.FactureResponseDTO;
import org.example.paiment_service.dto.response.TransactionPaiementResponseDTO;
import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.StatutPaiement;
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

    @GetMapping
    public ResponseEntity<PageResponse<TransactionPaiementResponseDTO>> rechercherTransactions(
            @RequestParam(required = false) StatutPaiement statut,
            @RequestParam(required = false) Contexte contexte,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paymentService.searchTransactions(statut, contexte, page, size));
    }

    @GetMapping("/{idTransaction}")
    public ResponseEntity<TransactionPaiementResponseDTO> getTransaction(@PathVariable Long idTransaction) {
        return ResponseEntity.ok(paymentService.getTransaction(idTransaction));
    }

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

    @GetMapping("/factures")
    public ResponseEntity<PageResponse<FactureResponseDTO>> getInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paymentService.getInvoices(page, size));
    }

    @GetMapping("/factures/{idFacture}")
    public ResponseEntity<FactureResponseDTO> getInvoice(@PathVariable Long idFacture) {
        return ResponseEntity.ok(paymentService.getInvoice(idFacture));
    }

    @GetMapping("/factures/{idFacture}/download-url")
    public ResponseEntity<String> telechargerFactureUrl(@PathVariable Long idFacture) {
        FactureResponseDTO facture = paymentService.getInvoice(idFacture);
        return ResponseEntity.ok(
                invoicePdfService.obtenirUrlTelechargementTemporaire(facture.getCheminFichierPdf()));
    }
}
