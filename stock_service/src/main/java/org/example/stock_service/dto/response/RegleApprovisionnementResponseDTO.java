package org.example.stock_service.dto.response;

import lombok.Data;

@Data
public class RegleApprovisionnementResponseDTO {
    private Long id;
    private int seuilAlerte;
    private int quantiteRecommandeAuto;
    private Boolean estActif;
}