package app.parkyn.dto;

import app.parkyn.model.Vehicle.VehicleStatus;
import jakarta.validation.constraints.NotNull;

public record VehicleStatusRequest(
    @NotNull VehicleStatus status
) {}
