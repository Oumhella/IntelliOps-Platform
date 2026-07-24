package org.example.notification_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notification_service.dto.event.NotificationEvent;
import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.StatutNotification;
import org.example.notification_service.repository.NotificationRepository;
import org.example.notification_service.strategy.NotificationChannelFactory;
import org.example.notification_service.strategy.NotificationChannelStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationChannelFactory channelFactory;

    @Override
    @Transactional
    public Notification processAndSend(NotificationEvent event) {
        Notification notification = Notification.builder()
                .type(event.getType())
                .recipientContact(event.getRecipientContact())
                .subject(event.getSubject())
                .contenu(event.getContenu())
                .statut(StatutNotification.QUEUED)
                .build();

        notification = notificationRepository.save(notification);

        try {
            NotificationChannelStrategy strategy = channelFactory.getStrategy(event.getType());
            boolean success = strategy.send(notification);

            if (success) {
                notification.setStatut(StatutNotification.SENT);
                notification.setSentAt(LocalDateTime.now());
            } else {
                notification.setStatut(StatutNotification.FAILED);
                notification.setErrorMessage("Delivery provider failed to dispatch.");
            }
        } catch (Exception ex) {
            log.error("Failed to process notification: ", ex);
            notification.setStatut(StatutNotification.FAILED);
            notification.setErrorMessage(ex.getMessage());
        }

        return notificationRepository.save(notification);
    }
}