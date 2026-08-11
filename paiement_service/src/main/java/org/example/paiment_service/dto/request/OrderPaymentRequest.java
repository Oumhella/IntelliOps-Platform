package org.example.paiment_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderPaymentRequest(@NotBlank @Size(max = 100) String idempotencyKey) {
}
