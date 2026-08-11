package org.example.paiment_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.paiment_service.entity.Contexte;
import org.example.paiment_service.entity.ModePaiement;

import java.math.BigDecimal;

@Data
public class InitierPaiementRequestDTO {
    @NotBlank
    private String idempotencyKey;
    @NotNull
    private Long referenceSourceId;
    @NotNull
    private Contexte typeContexte;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal montant;
    @NotNull
    private ModePaiement mode;
}
