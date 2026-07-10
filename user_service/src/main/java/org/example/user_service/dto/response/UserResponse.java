package org.example.user_service.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String firstname;
    private String lastname;
    private String phone;
    private String role;
    private boolean isActive;
}
