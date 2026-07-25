package org.example.notification_service.listener;

import org.example.common.event.NotificationEvent;
import org.example.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "${app.kafka.topic.notifications:notifications-topic}", groupId = "${spring.kafka.consumer.group-id:notification-group}")
    public void handleIncomingRequest(String payload) throws Exception {
        log.info("Kafka Event Received: {}", payload);
        NotificationEvent event = objectMapper.readValue(payload, NotificationEvent.class);
        notificationService.processAndSend(event);
    }

    @DltHandler
    public void handleDeadLetterPayload(String payload) {
        log.error("CRITICAL: Message sent to Dead Letter Queue (DLQ): {}", payload);
    }
}