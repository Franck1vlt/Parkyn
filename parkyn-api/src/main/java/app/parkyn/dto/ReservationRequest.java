package app.parkyn.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import app.parkyn.model.Reservation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReservationRequest(

    @NotNull
    UUID vehicleId,

    UUID driverId,
    String driverFirstName,
    String driverLastName,
    String driverEmail,

    UUID agentId,

    @NotNull
    OffsetDateTime startDate,

    @NotNull
    OffsetDateTime endDate,

    Reservation.Condition conditionBefore,

    @Positive
    Integer mileageStart,

    String notesBefore
) {}
