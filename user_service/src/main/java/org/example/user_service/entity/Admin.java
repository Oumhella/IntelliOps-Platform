package org.example.user_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("ADMIN") // C'est la valeur qui sera écrite dans la colonne user_type
@Getter
@Setter
public class Admin extends User {

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "activity_type")
    private String activityType;

    @Override
    public Role getRole() {
        return Role.ROLE_ADMIN; // S'aligne avec ton énumération de sécurité
    }
}