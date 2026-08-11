package org.example.paiment_service.client;

import org.example.common.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "lead-service", path = "/api/v1/commandes", configuration = FeignClientConfig.class)
public interface OrderClient {

    @GetMapping("/{id}")
    OrderSummary getOrder(@PathVariable("id") Long id);

    @PatchMapping("/{id}/payment-status")
    OrderSummary updatePaymentStatus(@PathVariable("id") Long id, @RequestBody PaymentStatusUpdate request);

    record OrderSummary(
            Long idCommande,
            BigDecimal totalPrix,
            String statutCommande,
            String statutPaiement,
            ClientInfo infosClient
    ) {
    }

    record ClientInfo(String email) {
    }

    record PaymentStatusUpdate(String status) {
    }
}
