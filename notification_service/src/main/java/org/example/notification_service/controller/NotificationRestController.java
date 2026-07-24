package org.example.notification_service.controller;

import org.example.notification_service.dto.event.NotificationEvent;
import org.example.notification_service.entity.Notification;
import org.example.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationRestController {

    private final NotificationService notificationService;

    @PostMapping("/direct")
    public ResponseEntity<Notification> postDirectNotif(@Valid @RequestBody NotificationEvent event) {
        Notification notification = notificationService.processAndSend(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }
}