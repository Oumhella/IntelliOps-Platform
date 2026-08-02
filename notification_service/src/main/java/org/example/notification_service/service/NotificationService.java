package org.example.notification_service.service;

import org.example.common.event.NotificationEvent;
import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.StatutNotification;
import org.example.notification_service.entity.TypeNotification;
import org.example.common.dto.PageResponse;

public interface NotificationService {
    Notification processAndSend(NotificationEvent event);
    Notification getById(Long idNotification);
    PageResponse<Notification> search(StatutNotification statut, TypeNotification type, int page, int size);
}
