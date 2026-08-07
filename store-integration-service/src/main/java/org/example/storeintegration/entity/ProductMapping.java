package org.example.storeintegration.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "integration_product_mappings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"connection_id", "external_variant_id"}),
        @UniqueConstraint(columnNames = {"connection_id", "internal_product_id"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductMapping {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "connection_id", nullable = false) private StoreConnection connection;
    @Column(name = "enterprise_id", nullable = false) private Long enterpriseId;
    @Column(name = "external_product_id", nullable = false) private String externalProductId;
    @Column(name = "external_variant_id", nullable = false) private String externalVariantId;
    private String externalSku;
    @Column(nullable = false) private String externalName;
    @Column(name = "internal_product_id", nullable = false) private Long internalProductId;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void created() { createdAt = Instant.now(); }
}
