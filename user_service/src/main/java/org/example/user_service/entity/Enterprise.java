package org.example.user_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "enterprises")
@Getter
@Setter
@NoArgsConstructor
public class Enterprise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String companyName;

    @Column(nullable = false, length = 120)
    private String activityType;

    @Column(length = 160) private String legalName;
    @Column(length = 80) private String legalIdentifier;
    @Column(length = 80) private String taxIdentifier;
    @Column(length = 160) private String contactEmail;
    @Column(length = 40) private String contactPhone;
    @Column(length = 255) private String website;
    @Column(length = 255) private String addressLine1;
    @Column(length = 255) private String addressLine2;
    @Column(length = 100) private String city;
    @Column(length = 30) private String postalCode;
    @Column(length = 2) private String countryCode = "MA";
    @Column(length = 3) private String currencyCode = "MAD";
    @Column(length = 80) private String timezone = "Africa/Casablanca";

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
