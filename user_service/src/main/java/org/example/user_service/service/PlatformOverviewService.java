package org.example.user_service.service;

import lombok.RequiredArgsConstructor;
import org.example.user_service.dto.response.PlatformOverviewResponse;
import org.example.user_service.entity.Admin;
import org.example.user_service.entity.Enterprise;
import org.example.user_service.repository.AdminRepository;
import org.example.user_service.repository.EnterpriseRepository;
import org.example.user_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformOverviewService {

    private static final Long PLATFORM_ENTERPRISE_ID = 0L;

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final DiscoveryClient discoveryClient;

    @Value("${platform.expected-services:user-service,gateway-service,lead-service,stock-service,abonnement-service,paiement-service,delivery-service,notification-service,mcp-server}")
    private String expectedServices;

    @Transactional(readOnly = true)
    public PlatformOverviewResponse getOverview() {
        List<Enterprise> registeredEnterprises = enterpriseRepository.findAllByOrderByCreatedAtDesc();
        Set<Long> registeredIds = registeredEnterprises.stream().map(Enterprise::getId).collect(Collectors.toSet());
        List<PlatformOverviewResponse.TenantSummary> tenants = new java.util.ArrayList<>(registeredEnterprises.stream()
                .map(this::toTenantSummary)
                .toList());
        adminRepository.findAllByEnterpriseIdNotOrderByCreatedAtDesc(PLATFORM_ENTERPRISE_ID).stream()
                .filter(admin -> !registeredIds.contains(admin.getEnterpriseId()))
                .map(this::toLegacyTenantSummary)
                .forEach(tenants::add);

        Map<String, String> registeredServices = discoveryClient.getServices().stream()
                .collect(Collectors.toMap(
                        serviceId -> serviceId.toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        List<PlatformOverviewResponse.ServiceSummary> services = Arrays.stream(expectedServices.split(","))
                .map(String::trim)
                .filter(serviceId -> !serviceId.isEmpty())
                .distinct()
                .map(serviceId -> toServiceSummary(serviceId, registeredServices))
                .toList();

        long onlineServices = services.stream()
                .filter(service -> "ONLINE".equals(service.status()))
                .count();

        PlatformOverviewResponse.Totals totals = new PlatformOverviewResponse.Totals(
                tenants.size(),
                userRepository.countBusinessUsers(),
                userRepository.countActiveBusinessUsers(),
                onlineServices,
                services.size()
        );

        return new PlatformOverviewResponse(Instant.now(), totals, tenants, services);
    }

    private PlatformOverviewResponse.TenantSummary toTenantSummary(Enterprise enterprise) {
        return new PlatformOverviewResponse.TenantSummary(
                enterprise.getId(),
                enterprise.getCompanyName(),
                enterprise.getActivityType(),
                userRepository.countByEnterpriseId(enterprise.getId()),
                enterprise.isActive(),
                enterprise.getCreatedAt()
        );
    }

    private PlatformOverviewResponse.TenantSummary toLegacyTenantSummary(Admin admin) {
        return new PlatformOverviewResponse.TenantSummary(
                admin.getEnterpriseId(),
                admin.getCompanyName(),
                admin.getActivityType(),
                userRepository.countByEnterpriseId(admin.getEnterpriseId()),
                admin.isActive(),
                admin.getCreatedAt()
        );
    }

    private PlatformOverviewResponse.ServiceSummary toServiceSummary(
            String expectedServiceId,
            Map<String, String> registeredServices
    ) {
        String normalizedId = expectedServiceId.toLowerCase(Locale.ROOT);
        String registryId = registeredServices.get(normalizedId);
        int instanceCount = registryId == null ? 0 : discoveryClient.getInstances(registryId).size();
        String status = instanceCount > 0 ? "ONLINE" : "OFFLINE";

        return new PlatformOverviewResponse.ServiceSummary(
                normalizedId,
                toDisplayName(normalizedId),
                status,
                instanceCount
        );
    }

    private String toDisplayName(String serviceId) {
        return Arrays.stream(serviceId.replace("-service", "").split("-"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" ")) + " service";
    }
}
