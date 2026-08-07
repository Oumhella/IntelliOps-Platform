package org.example.lead_service.client;

import org.example.common.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "abonnement-service", configuration = FeignClientConfig.class)
public interface AbonnementClient {

    @GetMapping("/api/v1/abonnements/entitlement")
    Entitlement currentEntitlement();

    record Entitlement(boolean active, int monthlyOrderLimit, String reason) {
    }
}
