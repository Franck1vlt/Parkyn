package app.parkyn.controller;

import org.springframework.web.bind.annotation.RestController;

import app.parkyn.dto.NotificationResponse;
import app.parkyn.model.User;
import app.parkyn.service.NotificationService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("")
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(@AuthenticationPrincipal User user){
        var notifications = notificationService.getUserNotifications(user.getId())
            .stream()
            .map(NotificationResponse::from)
            .toList();
        return ResponseEntity.ok(notifications);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal User user, @PathVariable UUID id){
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal User user){
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.noContent().build();
    }
}
