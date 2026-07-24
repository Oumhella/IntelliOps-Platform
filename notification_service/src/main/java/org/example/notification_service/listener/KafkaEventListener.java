package org.example.notification_service.listener;

import org.example.notification_service.dto.event.NotificationEvent;
import org.example.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topic.notifications:notifications-topic}", groupId = "${spring.kafka.consumer.group-id:notification-group}")
    public void handleIncomingRequest(String payload) {
        log.info("Kafka Event Received: {}", payload);
        try {
            NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);
            notificationService.processAndSend(event);
        } catch (Exception e) {
            log.error("Error deserializing Kafka notification payload", e);
        }
    }
}