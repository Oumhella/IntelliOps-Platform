package org.example.paiment_service.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.dto.PageResponse;
import org.example.paiment_service.dto.request.InitierPaiementRequestDTO;
import org.example.paiment_service.dto.request.RemboursementRequestDTO;
import org.example.paiment_service.dto.request.ConsumePaymentRequest;
import org.example.paiment_service.dto.request.PreparePaymentRequestDTO;
import org.example.paiment_service.dto.request.OrderPaymentRequest;
import jakarta.validation.Valid;
import org.example.paiment_service.dto.response.FactureResponseDTO;
import org.example.paiment_service.dto.response.TransactionPaiementResponseDTO;
import org.example.paiment_service.dto.response.PaymentPreparationResponseDTO;
import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.StatutPaiement;
import org.example.paiment_service.service.InvoicePdfService;
import org.example.paiment_service.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
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
    public ResponseEntity<TransactionPaiementResponseDTO> initierPaiement(@Valid @RequestBody InitierPaiementRequestDTO request) {
        return new ResponseEntity<>(paymentService.initierPaiement(request), HttpStatus.CREATED);
    }

    @PostMapping("/prepare")
    public ResponseEntity<PaymentPreparationResponseDTO> prepareCardPayment(
            @Valid @RequestBody PreparePaymentRequestDTO request) {
        return new ResponseEntity<>(paymentService.prepareCardPayment(request), HttpStatus.CREATED);
    }

    @PostMapping("/orders/{orderId}/prepare")
    public ResponseEntity<PaymentPreparationResponseDTO> prepareOrderCardPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderPaymentRequest request) {
        return new ResponseEntity<>(paymentService.prepareOrderCardPayment(orderId, request), HttpStatus.CREATED);
    }

    @PostMapping("/orders/{orderId}/cod")
    public ResponseEntity<TransactionPaiementResponseDTO> initiateOrderCod(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderPaymentRequest request) {
        return new ResponseEntity<>(paymentService.initiateOrderCod(orderId, request), HttpStatus.CREATED);
    }

    @PostMapping("/orders/{orderId}/collect-cod")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOGISTIC', 'LIVREUR')")
    public ResponseEntity<TransactionPaiementResponseDTO> collectOrderCod(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.collectOrderCod(orderId));
    }

    @PostMapping("/{idTransaction}/finalize")
    public ResponseEntity<TransactionPaiementResponseDTO> finalizeCardPayment(@PathVariable Long idTransaction) {
        return ResponseEntity.ok(paymentService.finalizeCardPayment(idTransaction));
    }

    @PostMapping("/{idTransaction}/consume")
    public ResponseEntity<TransactionPaiementResponseDTO> consumeCompletedPayment(
            @PathVariable Long idTransaction,
            @Valid @RequestBody ConsumePaymentRequest request) {
        return ResponseEntity.ok(paymentService.consumeCompletedPayment(idTransaction, request));
    }

    @PostMapping("/{idTransaction}/rembourser")
    public ResponseEntity<TransactionPaiementResponseDTO> rembourser(
            @PathVariable Long idTransaction,
            @Valid @RequestBody RemboursementRequestDTO request) {
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

    @GetMapping(value = "/factures/{idFacture}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> telechargerFacture(@PathVariable Long idFacture) {
        FactureResponseDTO facture = paymentService.getInvoice(idFacture);
        byte[] pdf = invoicePdfService.lireFacturePdf(facture.getCheminFichierPdf());
        String filename = facture.getNumeroFactureUnique() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }
}
