package org.example.abonnement_service.dto.response;

import java.time.LocalDate;

/**
 * Authoritative subscription decision for the enterprise in the caller's JWT.
 */
public record EntitlementResponse(
        boolean active,
        Long subscriptionId,
        String planName,
        int monthlyOrderLimit,
        LocalDate validUntil,
        String reason
) {
}
