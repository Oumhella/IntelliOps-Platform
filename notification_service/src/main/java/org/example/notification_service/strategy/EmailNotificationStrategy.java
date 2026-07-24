package org.example.notification_service.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notification_service.entity.Notification;
import org.example.notification_service.entity.TypeNotification;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationStrategy implements NotificationChannelStrategy {

    private final JavaMailSender mailSender;

    @Override
    public TypeNotification getType() {
        return TypeNotification.EMAIL;
    }

    @Override
    public boolean send(Notification notification) {
        log.info("Sending EMAIL to '{}' with subject: '{}'", notification.getRecipientContact(), notification.getSubject());
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(notification.getRecipientContact());
            message.setSubject(notification.getSubject());
            message.setText(notification.getContenu());

            mailSender.send(message);
            log.info("Email successfully dispatched to {}", notification.getRecipientContact());
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to '{}': {}", notification.getRecipientContact(), e.getMessage(), e);
            return false;
        }
    }
}