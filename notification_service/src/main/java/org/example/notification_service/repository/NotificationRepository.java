package org.example.notification_service.repository;

import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.StatutNotification;
import org.example.notification_service.entity.TypeNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdNotificationAndEnterpriseId(Long idNotification, Long enterpriseId);

    @Query("""
            select notification from Notification notification
            where notification.enterpriseId = :enterpriseId
              and (:statut is null or notification.statut = :statut)
              and (:type is null or notification.type = :type)
            """)
    Page<Notification> search(
            @Param("enterpriseId") Long enterpriseId,
            @Param("statut") StatutNotification statut,
            @Param("type") TypeNotification type,
            Pageable pageable);
}
