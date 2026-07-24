package org.example.paiment_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "modeles_tokenisation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeleTokenisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long systemAccountId;

    @Column(nullable = false, unique = true)
    private String tokenCarteSecurise;

    public boolean verifierValidite() {
        return tokenCarteSecurise != null && !tokenCarteSecurise.isBlank();
    }
}