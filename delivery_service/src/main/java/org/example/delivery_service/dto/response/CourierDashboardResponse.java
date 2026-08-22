package org.example.delivery_service.dto.response;

import java.math.BigDecimal;

public record CourierDashboardResponse(
        long assignedToday,
        long activeDeliveries,
        long completedToday,
        long failedAttempts,
        BigDecimal codAwaitingReconciliation) {
}
