package org.example.lead_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CoordonneesClientDTO {
    @NotBlank
    @Size(max = 180)
    private String nomComplet;
    @Email
    @Size(max = 180)
    private String email;
    @NotBlank
    @Size(max = 40)
    private String telephone;
    @NotBlank
    @Size(max = 500)
    private String adresseLivraison;
    @NotBlank
    @Size(max = 120)
    private String ville;
}
