package org.example.user_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateRequest {

    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    private String firstname;

    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    private String lastname;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;
}