package org.example.storeintegration.repository;
import org.example.storeintegration.entity.ProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProductMappingRepository extends JpaRepository<ProductMapping, Long> {
    List<ProductMapping> findAllByConnectionIdAndEnterpriseIdOrderByExternalNameAsc(Long connectionId, Long enterpriseId);
    Optional<ProductMapping> findByConnectionIdAndExternalVariantId(Long connectionId, String externalVariantId);
    Optional<ProductMapping> findByIdAndEnterpriseId(Long id, Long enterpriseId);
}
