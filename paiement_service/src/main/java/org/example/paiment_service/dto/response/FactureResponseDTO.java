package org.example.paiment_service.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FactureResponseDTO {
    private Long id;
    private String numeroFactureUnique;
    private String cheminFichierPdf;
    private LocalDateTime dateEmission;
}