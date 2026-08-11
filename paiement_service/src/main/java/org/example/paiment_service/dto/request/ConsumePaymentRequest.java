package org.example.paiment_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.paiment_service.entity.Contexte;

import java.math.BigDecimal;

public record ConsumePaymentRequest(
        @NotNull Contexte expectedContext,
        @NotNull Long expectedSourceId,
        @NotNull @DecimalMin("0.01") BigDecimal expectedAmount,
        @NotBlank String consumptionReference) {
}
