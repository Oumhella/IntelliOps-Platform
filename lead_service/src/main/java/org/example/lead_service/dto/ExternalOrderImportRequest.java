package org.example.lead_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ExternalOrderImportRequest(
        @NotBlank String platform,
        @NotBlank String externalOrderId,
        @NotBlank String externalReference,
        @NotNull @Positive Long stockLocationId,
        @NotNull @Valid Customer customer,
        @NotBlank String paymentStatus,
        @NotBlank @Pattern(regexp = "(?i)MAD") String currency,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal totalAmount,
        @NotEmpty List<@Valid Line> items
) {
    public record Customer(@NotBlank String fullName, @Email String email, String phone,
                           @NotBlank String address, @NotBlank String city) {}
    public record Line(@NotNull @Positive Long productId, @Positive int quantity,
                       @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal unitPrice) {}
}
