package org.example.delivery_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CompleteDeliveryRequest(
        @NotBlank @Size(max = 255) String recipientName,
        @NotBlank @Size(max = 255) String signature,
        @DecimalMin(value = "0.0") BigDecimal collectedCodAmount,
        @Size(max = 1000) String codDiscrepancyNote,
        Double latitude,
        Double longitude) {
}
