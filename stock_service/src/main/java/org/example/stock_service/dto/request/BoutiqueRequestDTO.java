package org.example.stock_service.dto.request;

import lombok.Data;
import org.example.stock_service.entity.TypePlateforme;

@Data
public class BoutiqueRequestDTO {
    private String nomBoutique;
    private TypePlateforme plateformeType;
    private String cleApi;
    private Long adminId;
}