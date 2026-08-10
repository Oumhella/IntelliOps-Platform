package org.example.lead_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CreationCommandeRequest {
    @NotBlank
    @Size(max = 80)
    private String idempotencyKey;

    @NotNull
    private Long stockLocationId;

    /**
     * Required for manual leads. Ignored when the lead already has an imported order with lines.
     */
    @Valid
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        @NotNull
        private Long productId;
        @Positive
        private int quantity;
    }
}
