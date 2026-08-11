package org.example.storeintegration.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.storeintegration.domain.StorePlatform;
import java.time.Instant;

@Entity
@Table(name = "integration_oauth_states", indexes = @Index(name = "idx_oauth_state_hash", columnList = "state_hash", unique = true))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OAuthState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "state_hash", nullable = false, length = 64) private String stateHash;
    @Column(name = "enterprise_id", nullable = false) private Long enterpriseId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private StorePlatform platform;
    @Column(nullable = false) private String displayName;
    @Column(nullable = false, length = 500) private String storeUrl;
    @Column(name = "stock_location_id", nullable = false) private Long stockLocationId;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
}
