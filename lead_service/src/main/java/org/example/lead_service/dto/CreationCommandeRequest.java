package org.example.lead_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreationCommandeRequest {
    private double totalAmount;
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        private Long productId;
        private int quantity;
        private double unitPrice;
    }
}