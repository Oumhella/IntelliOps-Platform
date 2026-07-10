package org.example.user_service.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password; // Sera haché avec BCrypt

    private String firstname;
    private String lastname;
    private String phone;

    private boolean isActive = true;

    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId; // Isolation multi-tenant

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    // Méthode abstraite pour récupérer le rôle Spring Security
    @Transient
    public abstract Role getRole();

}
