package org.example.notification_service.controller;

import org.example.common.event.NotificationEvent;
import org.example.notification_service.entity.Notification;
import org.example.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.common.dto.PageResponse;
import org.example.notification_service.entity.StatutNotification;
import org.example.notification_service.entity.TypeNotification;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NotificationRestController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PageResponse<Notification>> search(
            @RequestParam(required = false) StatutNotification statut,
            @RequestParam(required = false) TypeNotification type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.search(statut, type, page, size));
    }

    @GetMapping("/{idNotification}")
    public ResponseEntity<Notification> getById(@PathVariable Long idNotification) {
        return ResponseEntity.ok(notificationService.getById(idNotification));
    }

    @PostMapping("/direct")
    public ResponseEntity<Notification> postDirectNotif(@Valid @RequestBody NotificationEvent event) {
        Notification notification = notificationService.processAndSend(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(notification);
    }
}
