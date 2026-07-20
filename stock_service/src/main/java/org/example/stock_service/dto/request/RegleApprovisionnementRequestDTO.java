package org.example.stock_service.dto.request;

import lombok.Data;

@Data
public class RegleApprovisionnementRequestDTO {
    private int seuilAlerte;
    private int quantiteRecommandeAuto;
    private Boolean estActif;
}