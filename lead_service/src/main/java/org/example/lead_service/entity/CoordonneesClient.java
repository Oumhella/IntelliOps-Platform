package org.example.lead_service.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordonneesClient {

    private String nomComplet;
    private String telephone;
    private String adresseLivraison;
    private String ville;
    }
