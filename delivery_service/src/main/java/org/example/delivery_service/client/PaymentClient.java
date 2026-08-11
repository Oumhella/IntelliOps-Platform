package org.example.delivery_service.client;

import org.example.common.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "paiement-service", path = "/api/v1/payments", configuration = FeignClientConfig.class)
public interface PaymentClient {

    @PostMapping("/orders/{orderId}/collect-cod")
    void collectCashOnDelivery(@PathVariable("orderId") Long orderId);
}
