package org.example.user_service.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record PlatformOverviewResponse(
                Instant generatedAt,
                Totals totals,
                List<TenantSummary> tenants,
                List<ServiceSummary> services) {
        public record Totals(
                        long enterprises,
                        long users,
                        long activeUsers,
                        long onlineServices,
                        long totalServices) {
        }

        public record TenantSummary(
                        Long enterpriseId,
                        String companyName,
                        String activityType,
                        long userCount,
                        boolean active,
                        LocalDateTime createdAt) {
        }

        public record ServiceSummary(
                        String serviceId,
                        String name,
                        String status,
                        int instanceCount) {
        }

        public record PlatformEvent(
                        String type,
                        String subject,
                        String detail,
                        Instant observedAt,
                        String severity) {
        }

        public record PlatformSettings(
                        String environment,
                        String authentication,
                        String serviceDiscovery,
                        List<String> expectedServices,
                        Instant generatedAt) {
        }
}
