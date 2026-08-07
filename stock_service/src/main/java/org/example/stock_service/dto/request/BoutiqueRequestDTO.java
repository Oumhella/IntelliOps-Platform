package org.example.stock_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.example.stock_service.entity.TypePlateforme;

@Data
public class BoutiqueRequestDTO {
    @NotBlank
    @Size(max = 120)
    private String nomBoutique;

    @NotNull
    private TypePlateforme plateformeType;
}
