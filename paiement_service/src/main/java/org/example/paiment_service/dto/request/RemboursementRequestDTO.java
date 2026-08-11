package org.example.paiment_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RemboursementRequestDTO {
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montant;
    @NotBlank
    private String motif;
}
