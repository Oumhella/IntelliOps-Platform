package org.example.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private Long enterpriseId;
    private TypeNotification type;
    private String recipientContact;
    private String subject;
    private String contenu;
}
