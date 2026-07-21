package org.example.stock_service.dto.response;

import lombok.Data;
import org.example.stock_service.entity.TypePlateforme;

@Data
public class BoutiqueResponseDTO {
    private Long idBoutique;
    private String nomBoutique;
    private TypePlateforme plateformeType;
    private Long adminId;
}