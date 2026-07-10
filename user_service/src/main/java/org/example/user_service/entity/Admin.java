package org.example.user_service.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ADMIN") // C'est la valeur qui sera écrite dans la colonne user_type
public class Admin extends User {

    @Override
    public Role getRole() {
        return Role.ROLE_ADMIN; // S'aligne avec ton énumération de sécurité
    }
}