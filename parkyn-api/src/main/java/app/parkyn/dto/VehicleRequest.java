package app.parkyn.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import app.parkyn.model.Vehicle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
    @NotBlank String plateNumber,
    @NotBlank String brand,
    @NotBlank String model,
    @NotNull Integer year,
    @NotNull Integer mileageKM,
    Vehicle.VehicleCondition condition,
    @JsonFormat(pattern = "yyyy-MM-dd") @NotNull LocalDate commissionedAt
) {}
