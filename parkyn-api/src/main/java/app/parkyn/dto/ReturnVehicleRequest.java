package app.parkyn.dto;

import app.parkyn.model.Reservation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReturnVehicleRequest(
    @NotNull Reservation.Condition conditionAfter,
    @NotNull @Positive Integer mileageEnd,
    @NotNull Boolean damageReported,
    String damageDetails,
    String notesAfter
) {}
