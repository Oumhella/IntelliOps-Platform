package org.example.lead_service.dto;

import java.util.List;

public record LogisticsReadinessDTO(
        Long orderId,
        String reference,
        boolean ready,
        List<Check> checks) {

    public record Check(
            String code,
            String label,
            boolean passed,
            String detail) {
    }
}
