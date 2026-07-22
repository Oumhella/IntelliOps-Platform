package org.example.paiment_service.dto.request;

import lombok.Data;

@Data
public class RemboursementRequestDTO {
    private double montant;
    private String motif;
}