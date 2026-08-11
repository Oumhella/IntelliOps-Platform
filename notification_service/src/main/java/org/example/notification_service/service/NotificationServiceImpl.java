package org.example.notification_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.event.NotificationEvent;
import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.StatutNotification;
import org.example.notification_service.repository.NotificationRepository;
import org.example.notification_service.strategy.NotificationChannelFactory;
import org.example.notification_service.strategy.NotificationChannelStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import org.example.common.dto.PageResponse;
import org.example.common.security.TenantContext;
import org.example.common.exception.ResourceNotFoundException;
import org.example.notification_service.entity.TypeNotification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationChannelFactory channelFactory;

    @Override
    @Transactional
    public Notification processAndSend(NotificationEvent event) {
        Long enterpriseId = TenantContext.getEnterpriseId() != null
                ? TenantContext.getEnterpriseId()
                : event.getEnterpriseId();
        if (enterpriseId == null) {
            throw new IllegalArgumentException("Notification enterpriseId is required");
        }
        org.example.notification_service.entity.TypeNotification entityType =
                org.example.notification_service.entity.TypeNotification.valueOf(event.getType().name());
        if (entityType != TypeNotification.EMAIL) {
            throw new IllegalArgumentException(
                    "Notification channel " + entityType + " has no configured delivery provider.");
        }

        Notification notification = Notification.builder()
                .enterpriseId(enterpriseId)
                .type(entityType)
                .recipientContact(event.getRecipientContact())
                .subject(event.getSubject())
                .contenu(event.getContenu())
                .statut(StatutNotification.QUEUED)
                .build();

        notification = notificationRepository.save(notification);

        try {
            NotificationChannelStrategy strategy = channelFactory.getStrategy(entityType);
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

    @Override
    @Transactional(readOnly = true)
    public Notification getById(Long idNotification) {
        return notificationRepository.findByIdNotificationAndEnterpriseId(
                        idNotification, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with ID: " + idNotification));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Notification> search(
            StatutNotification statut, TypeNotification type, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(
                notificationRepository.search(
                        TenantContext.requireEnterpriseId(), statut, type, pageable),
                notification -> notification);
    }
}
