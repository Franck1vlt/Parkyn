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
    Vehicle.VehicleCondition condition,
    VehicleStatus status,
    LocalDate commissionedAt,
    LocalDate createdAt
) {
    // inUse : le véhicule est dehors à cet instant (voir Vehicle.displayedStatus)
    public static VehicleResponse from(Vehicle vehicle, boolean inUse) {
        return new VehicleResponse(
            vehicle.getId(),
            vehicle.getPlateNumber(),
            vehicle.getBrand(),
            vehicle.getModel(),
            vehicle.getYear(),
            vehicle.getMileageKM(),
            vehicle.getCondition(),
            vehicle.displayedStatus(inUse),
            vehicle.getCommissionedAt(),
            vehicle.getCreatedAt().toLocalDate()
        );
    }

}
