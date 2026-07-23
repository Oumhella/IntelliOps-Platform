package org.example.delivery_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.example.delivery_service.entity.TypeTransporteur;

@Data
public class ExpedierLivraisonRequest {

    @NotNull(message = "referenceCommandeId is required")
    private Long referenceCommandeId;

    @NotNull(message = "typeTransporteur is required")
    private TypeTransporteur typeTransporteur;

    @PositiveOrZero(message = "montantACollecterCoD must be >= 0")
    private double montantACollecterCoD;

    // Optional depending on strategy chosen
    private String nomSociete;
    private String endpointApiUrl;
    private Long externalLivreurId;
}