package org.example.user_service.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("SUPER_ADMIN")
public class SuperAdmin extends User {

    @Override
    public Role getRole() {
        return Role.ROLE_SUPER_ADMIN;
    }
}
