package org.example.lead_service.dto;

import jakarta.validation.constraints.NotNull;
import org.example.lead_service.entity.StatutPaiementCommande;

public record UpdateOrderPaymentStatusRequest(@NotNull StatutPaiementCommande status) {
}
