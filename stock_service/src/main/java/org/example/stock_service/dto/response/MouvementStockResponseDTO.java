package org.example.stock_service.dto.response;

import lombok.Data;
import org.example.stock_service.entity.TypeMouvement;
import java.time.LocalDateTime;

@Data
public class MouvementStockResponseDTO {
    private Long id;
    private TypeMouvement typeMouvement;
    private int quantite;
    private LocalDateTime dateMouvement;
    private Long auteurId;
}