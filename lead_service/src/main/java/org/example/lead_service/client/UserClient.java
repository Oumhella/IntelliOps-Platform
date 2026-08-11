package org.example.lead_service.client;

import org.example.common.feign.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", path = "/api/v1/users", configuration = FeignClientConfig.class)
public interface UserClient {

    @GetMapping("/staff/{id}")
    UserSummary getStaffMember(@PathVariable("id") Long id);

    record UserSummary(Long id, String role, boolean active) {
    }
}
