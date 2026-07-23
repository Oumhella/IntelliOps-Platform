package org.example.delivery_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.delivery_service.entity.StatutLivraison;

@Data
public class UpdateStatutRequest {

    @NotNull(message = "statut is required")
    private StatutLivraison statut;
}