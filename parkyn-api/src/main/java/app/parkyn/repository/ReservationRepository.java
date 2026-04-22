package app.parkyn.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.parkyn.model.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    @Query("""
        SELECT COUNT(r) > 0 FROM Reservation r
        WHERE r.vehicle.id = :vehicleId
        AND r.returnedAt IS NULL
        AND r.startDate < :endDate
        AND r.endDate > :startDate
        """)
    boolean hasConflict(
        @Param("vehicleId") UUID vehicleId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate
    );
}
