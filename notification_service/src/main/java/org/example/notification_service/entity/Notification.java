package org.example.notification_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idNotification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeNotification type;

    @Column(nullable = false)
    private String recipientContact; // Email, Phone Number, or Push Device Token

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contenu;

    private String subject; // Added: Essential for Email/Push notifications

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutNotification statut;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    private String errorMessage;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.statut == null) {
            this.statut = StatutNotification.QUEUED;
        }
    }
}