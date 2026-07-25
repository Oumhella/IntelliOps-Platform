package org.example.lead_service.client;

import org.example.common.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "abonnement-service", configuration = FeignClientConfig.class)
public interface AbonnementClient {

    @GetMapping("/api/v1/abonnements/{id}/verifier-limite")
    Boolean verifierLimiteCommandes(
            @PathVariable("id") Long abonnementId,
            @RequestParam("commandesEffectuees") int commandesEffectuees
    );
}
