package org.example.lead_service.dto;

import jakarta.validation.constraints.NotNull;
import org.example.lead_service.entity.StatutCommande;

public record UpdateOrderFulfillmentStatusRequest(@NotNull StatutCommande status) {
}
