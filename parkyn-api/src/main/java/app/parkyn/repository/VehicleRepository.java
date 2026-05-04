package app.parkyn.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import app.parkyn.model.Vehicle;
import app.parkyn.model.Vehicle.VehicleStatus;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {
    
    List<Vehicle> findByStatusAndDeletedAtIsNull(VehicleStatus status);
}
