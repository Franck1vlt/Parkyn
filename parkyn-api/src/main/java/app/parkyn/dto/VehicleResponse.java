package app.parkyn.dto;

import java.time.LocalDate;
import java.util.UUID;

import app.parkyn.model.Vehicle;
import app.parkyn.model.Vehicle.VehicleStatus;

public record VehicleResponse(
    UUID id,
    String plateNumber,
    String brand,
    String model,
    Integer year,
    Integer mileageKM,
    VehicleStatus status,
    LocalDate commissionedAt,
    LocalDate createdAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
            vehicle.getId(),
            vehicle.getPlateNumber(),
            vehicle.getBrand(),
            vehicle.getModel(),
            vehicle.getYear(),
            vehicle.getMileageKM(),
            vehicle.getStatus(),
            vehicle.getCommissionedAt(),
            vehicle.getCreatedAt().toLocalDate()
        );
    }

}
