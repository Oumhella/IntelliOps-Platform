package org.example.delivery_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LivraisonEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.notifications:notifications-topic}")
    private String topicName;

    public void sendNotificationEvent(String recipientEmail, String subject, String content) {
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .type("EMAIL")
                    .recipientContact(recipientEmail)
                    .subject(subject)
                    .contenu(content)
                    .build();

            String jsonPayload = objectMapper.writeValueAsString(event);
            log.info("Publishing notification event to Kafka topic '{}': {}", topicName, jsonPayload);

            kafkaTemplate.send(topicName, jsonPayload);
        } catch (Exception e) {
            log.error("Failed to publish notification event to Kafka", e);
        }
    }
}