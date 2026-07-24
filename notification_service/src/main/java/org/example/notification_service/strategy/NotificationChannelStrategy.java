package org.example.notification_service.strategy;

import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.TypeNotification;

public interface NotificationChannelStrategy {
    TypeNotification getType();
    boolean send(Notification notification);
}
