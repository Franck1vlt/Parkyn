package app.parkyn.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import app.parkyn.model.Reservation;

public record ReservationResponse(
    UUID id,
    UUID vehicleId,
    UUID driverId,
    String driverFirstName,
    String driverLastName,
    UUID agentId,
    String agentFirstName,
    String agentLastName,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    OffsetDateTime returnedAt,
    String conditionBefore,
    Integer mileageStart,
    String notesBefore,
    String conditionAfter,
    Integer mileageEnd,
    Boolean damageReported,
    String damageDetails,
    String notesAfter,
    Reservation.Status status,
    OffsetDateTime cancelledAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return from(reservation, OffsetDateTime.now());
    }

    // now : instant du statut calculé (le même que celui d'un filtre, pour une liste)
    public static ReservationResponse from(Reservation reservation, OffsetDateTime now) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getVehicle() != null ? reservation.getVehicle().getId() : null,
            reservation.getDriver() != null ? reservation.getDriver().getId() : null,
            reservation.getDriver() != null ? reservation.getDriver().getFirstName() : null,
            reservation.getDriver() != null ? reservation.getDriver().getLastName() : null,
            reservation.getAgent() != null ? reservation.getAgent().getId() : null,
            reservation.getAgent() != null ? reservation.getAgent().getFirstName() : null,
            reservation.getAgent() != null ? reservation.getAgent().getLastName() : null,
            reservation.getStartDate(),
            reservation.getEndDate(),
            reservation.getReturnedAt(),
            reservation.getConditionBefore() != null ? reservation.getConditionBefore().name() : null,
            reservation.getMileageStart(),
            reservation.getNotesBefore(),
            reservation.getConditionAfter() != null ? reservation.getConditionAfter().name() : null,
            reservation.getMileageEnd(),
            reservation.getDamageReported(),
            reservation.getDamageDetails(),
            reservation.getNotesAfter(),
            reservation.statusAt(now),
            reservation.getCancelledAt()
        );
    }
}
