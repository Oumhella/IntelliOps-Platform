package org.example.delivery_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String type;            // "EMAIL", "SMS", etc.
    private String recipientContact; // Email address
    private String subject;
    private String contenu;
}