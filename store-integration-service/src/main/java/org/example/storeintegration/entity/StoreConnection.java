package org.example.storeintegration.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.storeintegration.domain.ConnectionStatus;
import org.example.storeintegration.domain.StorePlatform;
import java.time.Instant;

@Entity
@Table(name = "store_connections", uniqueConstraints = @UniqueConstraint(columnNames = {"enterprise_id", "platform", "store_url"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StoreConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "enterprise_id", nullable = false) private Long enterpriseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private StorePlatform platform;
    @Column(nullable = false) private String displayName;
    @Column(name = "store_url", nullable = false, length = 500) private String storeUrl;
    @Column(name = "stock_location_id", nullable = false) private Long stockLocationId;
    @Column(name = "encrypted_credentials", nullable = false, columnDefinition = "text") private String encryptedCredentials;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ConnectionStatus status;
    @Column(nullable = false) private boolean webhooksActive;
    @Column(length = 1000) private String lastError;
    private Instant lastSyncAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
