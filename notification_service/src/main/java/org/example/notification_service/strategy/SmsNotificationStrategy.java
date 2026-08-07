package org.example.notification_service.strategy;

import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.TypeNotification;
import org.example.notification_service.strategy.NotificationChannelStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsNotificationStrategy implements NotificationChannelStrategy {

    @Override
    public TypeNotification getType() {
        return TypeNotification.SMS;
    }

    @Override
    public boolean send(Notification notification) {
        throw new UnsupportedOperationException("No SMS delivery provider is configured.");
    }
}
