package org.example.abonnement_service.client;

import org.example.common.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "paiement-service", path = "/api/v1/payments", configuration = FeignClientConfig.class)
public interface PaymentClient {

    @PostMapping("/prepare")
    PaymentPreparation prepare(@RequestBody PreparePayment request);

    @PostMapping("/{id}/finalize")
    PaymentSummary finalizePayment(@PathVariable("id") Long id);

    @PostMapping("/{id}/consume")
    PaymentSummary consume(@PathVariable("id") Long id, @RequestBody ConsumePayment request);

    record ConsumePayment(
            String expectedContext,
            Long expectedSourceId,
            BigDecimal expectedAmount,
            String consumptionReference) {
    }

    record PreparePayment(
            String idempotencyKey,
            Long referenceSourceId,
            String typeContexte,
            BigDecimal montant) {
    }

    record PaymentPreparation(
            Long paymentId,
            String clientSecret,
            String publishableKey,
            BigDecimal amount,
            String currency,
            String status) {
    }

    record PaymentSummary(
            Long id,
            Long referenceSourceId,
            String typeContexte,
            BigDecimal montant,
            String statut,
            String consumptionReference) {
    }
}
