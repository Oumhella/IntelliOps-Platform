package org.example.stock_service.event;

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
public class StockAlertProducer {
    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper mapper;
    @Value("${app.kafka.topic.notifications:notifications-topic}") private String topic;
    public void send(Long tenant,String email,String product,String location,int available,int threshold,int recommended){
        if(email==null||email.isBlank()){log.warn("Low-stock alert skipped: recipient missing");return;}
        try{
            String body="Low-stock alert for "+product+" at "+location+". Available: "+available+", threshold: "+threshold+", recommended replenishment: "+recommended+" units.";
            var event=NotificationEvent.builder().enterpriseId(tenant).type(TypeNotification.EMAIL).recipientContact(email).subject("Low stock: "+product).contenu(body).build();
            kafka.send(topic,mapper.writeValueAsString(event));
        }catch(Exception e){log.error("Failed to publish low-stock alert",e);}
    }
}
