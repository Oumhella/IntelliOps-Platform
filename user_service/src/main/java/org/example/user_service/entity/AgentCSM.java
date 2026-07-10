package org.example.user_service.entity;


import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CSM")
public class AgentCSM extends User{

    private int nbrCommandeTraitee;

    @Override
    public Role getRole() {
        return Role.ROLE_CSM;
    }
}
