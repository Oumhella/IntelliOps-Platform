package org.example.notification_service.repository;

import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.StatutNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByStatut(StatutNotification statut);
}