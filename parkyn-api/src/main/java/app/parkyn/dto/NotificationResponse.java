package app.parkyn.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import app.parkyn.model.Notification;
import app.parkyn.model.Notification.NotificationType;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    boolean isRead,
    UUID reservationId,
    OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.isRead(),
            notification.getReservation() != null ? notification.getReservation().getId() : null,
            notification.getCreatedAt()
        );
    }
}
