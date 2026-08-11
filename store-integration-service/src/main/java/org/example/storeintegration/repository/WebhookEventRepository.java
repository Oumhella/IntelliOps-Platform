package org.example.storeintegration.repository;
import org.example.storeintegration.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByConnectionIdAndExternalEventId(Long connectionId, String externalEventId);
    List<WebhookEvent> findTop50ByConnectionEnterpriseIdOrderByReceivedAtDesc(Long enterpriseId);
}
