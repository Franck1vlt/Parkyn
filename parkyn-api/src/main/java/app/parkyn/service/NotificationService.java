package app.parkyn.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.model.Notification;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Notification.NotificationType;
import app.parkyn.repository.NotificationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public void createForAllAdminsAndAgents(NotificationType notificationType, Reservation reservation) {
        var users = userRepository.findByNamesAndNoTenant(List.of(User.Role.ADMIN.name(), User.Role.AGENT.name()));
        var notifications = users.stream().map(user -> {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setReservation(reservation);
            notification.setType(notificationType);
            return notification;
        }).toList();
        notificationRepository.saveAll(notifications);
    }

    // Annulation : les notifications de la réservation disparaissent, une seule notification d'annulation les remplace
    @Transactional
    public void replaceWithCancellationNotice(Reservation reservation) {
        notificationRepository.deleteByReservationId(reservation.getId());
        createForAllAdminsAndAgents(NotificationType.RESERVATION_CANCELLED, reservation);
    }

    @Transactional
    public List<Notification> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        var notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));
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
    public void checkEndingSoon() {
        forEachTenant(this::checkEndingSoon);
    }

    // Retour en retard : agents et administrateurs sont prévenus, une seule fois, qu'une réservation est en attente
    @Scheduled(cron = "0 */15 * * * *")
    public void checkOnHold() {
        forEachTenant(now -> reservationRepository.findAllById(reservationRepository.findOnHoldIds(null, now))
            .forEach(reservation -> {
                if (!notificationRepository.existsByReservationIdAndType(reservation.getId(), NotificationType.RESERVATION_ON_HOLD)) {
                    createForAllAdminsAndAgents(NotificationType.RESERVATION_ON_HOLD, reservation);
                }
            }));
    }

    // Aucun utilisateur connecté : chaque tenant est traité sous son propre tenant, dans une nouvelle transaction
    // (la session Hibernate lit son tenant à l'ouverture). Requêtes filtrées et notifications rattachées automatiquement.
    private void forEachTenant(java.util.function.Consumer<java.time.OffsetDateTime> task) {
        var now = java.time.OffsetDateTime.now();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reservationRepository.findTenantIdsWithActiveReservations().forEach(tenantId ->
            TenantContext.runAs(tenantId, () -> transaction.executeWithoutResult(status -> task.accept(now))));
    }

    private void checkEndingSoon(java.time.OffsetDateTime now) {
        var in24h = now.plusHours(24);
        var in7days = now.plusDays(7);
        var reservations = reservationRepository.findByReturnedAtIsNullAndCancelledAtIsNullAndEndDateBetween(now, in7days);

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
