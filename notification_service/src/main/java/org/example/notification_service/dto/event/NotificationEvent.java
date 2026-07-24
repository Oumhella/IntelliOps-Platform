package org.example.notification_service.dto.event;

import org.example.notification_service.entity.TypeNotification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    @NotNull(message = "Notification type is required")
    private TypeNotification type;

    @NotBlank(message = "Recipient contact is required")
    private String recipientContact;

    private String subject;

    @NotBlank(message = "Content is required")
    private String contenu;
}