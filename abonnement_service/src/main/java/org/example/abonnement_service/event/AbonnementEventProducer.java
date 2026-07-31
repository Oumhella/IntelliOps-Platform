package org.example.abonnement_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.event.NotificationEvent;
import org.example.common.event.TypeNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbonnementEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.notifications:notifications-topic}")
    private String topicName;

    public void sendSubscriptionNotification(String recipientEmail, String subject, String content) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Recipient email is empty, skipping subscription notification event.");
            return;
        }

        try {
            NotificationEvent event = NotificationEvent.builder()
                    .type(TypeNotification.EMAIL)
                    .recipientContact(recipientEmail)
                    .subject(subject)
                    .contenu(content)
                    .build();

            String jsonPayload = objectMapper.writeValueAsString(event);
            log.info("Publishing Subscription Notification event to Kafka topic '{}': {}", topicName, jsonPayload);

            kafkaTemplate.send(topicName, jsonPayload);
        } catch (Exception e) {
            log.error("Failed to publish subscription notification event to Kafka", e);
        }
    }
}
