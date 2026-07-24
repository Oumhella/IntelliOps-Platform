package org.example.notification_service.service;

import org.example.notification_service.dto.event.NotificationEvent;
import org.example.notification_service.entity.Notification;

public interface NotificationService {
    Notification processAndSend(NotificationEvent event);
}
