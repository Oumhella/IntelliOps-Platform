package org.example.common.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @NotNull
    private TypeNotification type;
    @NotBlank
    @Size(max = 320)
    private String recipientContact;
    @Size(max = 250)
    private String subject;
    @NotBlank
    @Size(max = 5000)
    private String contenu;
}
