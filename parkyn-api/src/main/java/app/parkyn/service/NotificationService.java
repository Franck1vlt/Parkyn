package app.parkyn.service;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.parkyn.model.Notification;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Notification.NotificationType;
import app.parkyn.repository.NotificationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public void createForAllAdminsAndAgents(NotificationType notificationType, Reservation reservation) {
        var users = userRepository.findByRoleIn(List.of(User.Role.ADMIN, User.Role.AGENT));
        var notifications = users.stream().map(user -> {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setReservation(reservation);
            notification.setType(notificationType);
            return notification;
        }).toList();
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public List<Notification> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        var notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkEndingSoon() {
        var now = java.time.OffsetDateTime.now();
        var in24h = now.plusHours(24);
        var in7days = now.plusDays(7);
        var reservations = reservationRepository.findByReturnedAtIsNullAndEndDateBetween(now, in7days);

        reservations.forEach(reservation -> {
            var duration = java.time.Duration.between(reservation.getStartDate(), reservation.getEndDate()).toDays();
            var endDate = reservation.getEndDate();

            if (duration > 7 && endDate.isAfter(in7days.minusHours(1)) && endDate.isBefore(in7days.plusHours(1))) {
                if (!notificationRepository.existsByReservationIdAndType(reservation.getId(), NotificationType.RESERVATION_ENDING_SOON)) {
                    createForAllAdminsAndAgents(NotificationType.RESERVATION_ENDING_SOON, reservation);
                }
            }
            else if (duration <= 7 && endDate.isAfter(in24h.minusHours(1)) && endDate.isBefore(in24h.plusHours(1))) {
                if (!notificationRepository.existsByReservationIdAndType(reservation.getId(), NotificationType.RESERVATION_ENDING_SOON)) {
                    createForAllAdminsAndAgents(NotificationType.RESERVATION_ENDING_SOON, reservation);
                }
            }
        }); 
    }
}
