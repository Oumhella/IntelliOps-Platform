package org.example.user_service.dto.response;

import lombok.*;
import org.springframework.stereotype.Service;

@Getter
@Setter
@Data
@AllArgsConstructor // <--- Crée le constructeur à 6 arguments requis par ton UserService
@NoArgsConstructor  // <--- Crée un constructeur vide (obligatoire pour la désérialisation Jackson)
public class AuthResponse {
    private String token;
    private String email;
    private String firstname;
    private String lastname;
    private String role;
    private Long enterpriseId;

}
