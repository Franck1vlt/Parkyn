package app.parkyn.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import app.parkyn.model.Reservation;

public record ReservationResponse(
    UUID id,
    UUID vehicleId,
    UUID driverId,
    UUID agentId,
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
    String notesAfter
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getVehicle() != null ? reservation.getVehicle().getId() : null,
            reservation.getDriver() != null ? reservation.getDriver().getId() : null,
            reservation.getAgent() != null ? reservation.getAgent().getId() : null,
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
            reservation.getNotesAfter()
        );
    }
}
