package org.example.delivery_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.delivery_service.entity.MotifEchecLivraison;

public record FailedDeliveryAttemptRequest(
        @NotNull MotifEchecLivraison reason,
        @Size(max = 1000) String note,
        Double latitude,
        Double longitude) {
}
