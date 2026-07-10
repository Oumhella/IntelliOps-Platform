package org.example.user_service.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCreationRequest {
    private String email;
    private String password;
    private String firstname;
    private String lastname;
    private String phone;
    private String role; // "CSM" ou "LOGISTIC"
}
