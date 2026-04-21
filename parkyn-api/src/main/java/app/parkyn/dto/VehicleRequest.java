package app.parkyn.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
    @NotBlank String plateNumber,
    @NotBlank String brand,
    @NotBlank String model,
    @NotNull Integer year,
    @NotNull Integer mileageKM,
    @NotNull LocalDate commissionedAt
) {}
