package app.parkyn.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import app.parkyn.model.Notification;
import app.parkyn.model.Notification.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, UUID>{
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID notificationId, UUID userId);

    boolean existsByReservationIdAndType(UUID reservationId, NotificationType type);

    void deleteByReservationId(UUID reservationId);

}
