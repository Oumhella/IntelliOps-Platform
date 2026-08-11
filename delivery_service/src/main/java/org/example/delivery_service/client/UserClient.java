package org.example.delivery_service.client;

import org.example.common.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", path = "/api/v1/users", configuration = FeignClientConfig.class)
public interface UserClient {

    @GetMapping("/staff/couriers/{id}")
    UserSummary getActiveCourier(@PathVariable("id") Long id);
}
