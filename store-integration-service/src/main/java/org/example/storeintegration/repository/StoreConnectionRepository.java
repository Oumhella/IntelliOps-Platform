package org.example.storeintegration.repository;
import org.example.storeintegration.entity.StoreConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface StoreConnectionRepository extends JpaRepository<StoreConnection, Long> {
    List<StoreConnection> findAllByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId);
    Optional<StoreConnection> findByIdAndEnterpriseId(Long id, Long enterpriseId);
    Optional<StoreConnection> findByEnterpriseIdAndPlatformAndStoreUrl(Long enterpriseId, org.example.storeintegration.domain.StorePlatform platform, String storeUrl);
}
