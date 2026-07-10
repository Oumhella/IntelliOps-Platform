package org.example.user_service.entity;


import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("LOGISTIC")
public class AgentLogistic extends User{

    private String zoneEntrepot;

    @Override
    public Role getRole() {
        return Role.ROLE_LOGISTIC;
    }
}
