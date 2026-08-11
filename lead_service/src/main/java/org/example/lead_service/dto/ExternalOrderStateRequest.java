package org.example.lead_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ExternalOrderStateRequest(
        @NotBlank String platform,
        @NotBlank String externalOrderId,
        @NotBlank String paymentStatus,
        boolean cancelled,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal totalAmount,
        @NotNull @Valid List<ExternalOrderImportRequest.Line> items
) {}
