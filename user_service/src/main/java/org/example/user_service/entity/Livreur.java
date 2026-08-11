package org.example.user_service.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("LIVREUR")
public class Livreur extends User {

    @Override
    public Role getRole() {
        return Role.ROLE_LIVREUR;
    }
}
