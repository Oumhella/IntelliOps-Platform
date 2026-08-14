package org.example.user_service.event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.event.NotificationEvent;
import org.example.common.event.TypeNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
@Slf4j @Component @RequiredArgsConstructor
public class PasswordResetEventProducer {
 private final KafkaTemplate<String,String> kafkaTemplate; private final ObjectMapper objectMapper;
 @Value("${app.kafka.topic.notifications:notifications-topic}") private String topic;
 public void send(Long enterpriseId,String email,String link){
  try { var event=NotificationEvent.builder().enterpriseId(enterpriseId).type(TypeNotification.EMAIL).recipientContact(email)
    .subject("Reset your IntelliOps password").contenu("A password reset was requested for your IntelliOps account. This link expires in 30 minutes:\n\n"+link+"\n\nIf you did not request this, ignore this email.").build();
    kafkaTemplate.send(topic,objectMapper.writeValueAsString(event));
  } catch(Exception e){log.error("Could not publish password-reset email",e);}
 }
}
