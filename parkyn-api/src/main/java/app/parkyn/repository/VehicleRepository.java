package app.parkyn.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import app.parkyn.model.Vehicle;
import jakarta.persistence.LockModeType;
import app.parkyn.model.Vehicle.VehicleStatus;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {
    
    List<Vehicle> findByStatusAndDeletedAtIsNull(VehicleStatus status);

    // Verrou sur la ligne du véhicule jusqu'à la fin de la transaction (SELECT ... FOR UPDATE) :
    // les demandes de réservation d'un même véhicule passent l'une après l'autre, comme dans une file
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") UUID id);
}
