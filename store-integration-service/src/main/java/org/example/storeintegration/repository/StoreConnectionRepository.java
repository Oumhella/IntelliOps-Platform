package org.example.storeintegration.repository;
import org.example.storeintegration.entity.StoreConnection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface StoreConnectionRepository extends JpaRepository<StoreConnection, Long> {
    List<StoreConnection> findAllByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId);
    Optional<StoreConnection> findByIdAndEnterpriseId(Long id, Long enterpriseId);
    Optional<StoreConnection> findByEnterpriseIdAndPlatformAndStoreUrl(Long enterpriseId, org.example.storeintegration.domain.StorePlatform platform, String storeUrl);

    /**
     * Serializes deliveries for one connection. Shopify may deliver create/update
     * notifications concurrently, including duplicate delivery IDs. Locking the
     * connection preserves their order and makes the event uniqueness check
     * race-safe.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from StoreConnection connection where connection.id = :id")
    Optional<StoreConnection> findByIdForWebhook(@Param("id") Long id);
}
