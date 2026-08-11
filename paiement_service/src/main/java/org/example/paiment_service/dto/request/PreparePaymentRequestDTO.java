package org.example.paiment_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.paiment_service.entity.Contexte;

import java.math.BigDecimal;

public record PreparePaymentRequestDTO(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotNull @Positive Long referenceSourceId,
        @NotNull Contexte typeContexte,
        @NotNull @DecimalMin(value = "0.01") BigDecimal montant) {
}
