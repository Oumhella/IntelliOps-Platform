package org.example.user_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    // Infos Entreprise (portées par l'Admin)
    private String companyName;
    private String activityType; // "Ecommerçant" ou "Centre de confirmation"

    // Infos Personnelles de l'Admin
    private String email;
    private String password;
    private String firstname;
    private String lastname;
    private String phone;
}