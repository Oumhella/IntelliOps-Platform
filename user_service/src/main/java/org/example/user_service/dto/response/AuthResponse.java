package org.example.user_service.dto.response;

import lombok.*;
import org.springframework.stereotype.Service;

@Getter
@Setter
@Data
@AllArgsConstructor
@NoArgsConstructor  // <--- Crée un constructeur vide (obligatoire pour la désérialisation Jackson)
public class AuthResponse {
    private String token;
    private Long id;
    private String email;
    private String firstname;
    private String lastname;
    private String role;
    private Long enterpriseId;

}
