package org.example.storeintegration.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.storeintegration.domain.WebhookEventStatus;
import java.time.Instant;

@Entity
@Table(name = "integration_webhook_events", uniqueConstraints = @UniqueConstraint(columnNames = {"connection_id", "external_event_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "connection_id", nullable = false) private StoreConnection connection;
    @Column(name = "external_event_id", nullable = false) private String externalEventId;
    @Column(nullable = false) private String topic;
    @Column(nullable = false, length = 64) private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private WebhookEventStatus status;
    @Column(length = 1200) private String errorMessage;
    @Column(nullable = false, updatable = false) private Instant receivedAt;
    private Instant processedAt;
    @PrePersist void received() { receivedAt = Instant.now(); }
}
