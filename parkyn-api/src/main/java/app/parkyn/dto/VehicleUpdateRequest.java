package app.parkyn.dto;

import java.time.LocalDate;

public record VehicleUpdateRequest(
    String plateNumber,
    String brand,
    String model,
    Integer year,
    Integer mileageKM,
    LocalDate commissionedAt
) {}
