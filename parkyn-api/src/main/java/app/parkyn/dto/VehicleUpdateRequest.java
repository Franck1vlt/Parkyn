package app.parkyn.dto;

import java.time.LocalDate;

import app.parkyn.model.Vehicle;

public record VehicleUpdateRequest(
    String plateNumber,
    String brand,
    String model,
    Integer year,
    Integer mileageKM,
    Vehicle.VehicleCondition condition,
    LocalDate commissionedAt
) {}
