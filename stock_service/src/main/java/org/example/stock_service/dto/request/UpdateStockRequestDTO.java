package org.example.stock_service.dto.request;

import lombok.Data;
import org.example.stock_service.entity.TypeMouvement;

@Data
public class UpdateStockRequestDTO {
    private int quantite;
    private TypeMouvement typeMouvement;
}