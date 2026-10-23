package app.parkyn.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.parkyn.model.Reservation;
import app.parkyn.model.User;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    @EntityGraph(attributePaths = {"driver", "agent", "vehicle"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    // Règle unique des créneaux occupés, identique à la contrainte reservations_no_overlap (V14) :
    // une réservation occupe [début, fin[, ou [début, retour[ si le véhicule est rendu en avance.
    // vehicleId facultatif : null = tous les véhicules du tenant.
    @Query("""
        SELECT DISTINCT r.vehicle.id FROM Reservation r
        WHERE (:vehicleId IS NULL OR r.vehicle.id = :vehicleId)
        AND r.startDate < :endDate
        AND (r.returnedAt IS NULL OR r.returnedAt > r.startDate)
        AND (CASE WHEN r.returnedAt IS NOT NULL AND r.returnedAt < r.endDate THEN r.returnedAt ELSE r.endDate END) > :startDate
        """)
    Set<UUID> findVehicleIdsBookedBetween(
        @Param("vehicleId") UUID vehicleId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate
    );

    default boolean hasConflict(UUID vehicleId, OffsetDateTime startDate, OffsetDateTime endDate) {
        return !findVehicleIdsBookedBetween(vehicleId, startDate, endDate).isEmpty();
    }

    // Véhicules dehors à cet instant : réservation commencée et pas encore retournée (même après sa fin, en retard).
    // vehicleId facultatif : null = tous les véhicules du tenant.
    @Query("""
        SELECT DISTINCT r.vehicle.id FROM Reservation r
        WHERE (:vehicleId IS NULL OR r.vehicle.id = :vehicleId)
        AND r.returnedAt IS NULL
        AND r.startDate <= :now
        """)
    Set<UUID> findVehicleIdsInUseAt(@Param("vehicleId") UUID vehicleId, @Param("now") OffsetDateTime now);

    List<Reservation> findByReturnedAtIsNullAndEndDateBetween(OffsetDateTime start, OffsetDateTime end);

    // Appelée hors connexion (traitement planifié) : sans filtre, elle liste les tenants de toutes les entreprises
    @Query("SELECT DISTINCT r.tenantId FROM Reservation r WHERE r.returnedAt IS NULL")
    List<UUID> findTenantIdsWithActiveReservations();

    List<Reservation> findTop5ByOrderByCreatedAtDesc();

    List<Reservation> findTop5ByAgentIdOrderByCreatedAtDesc(UUID agentId);

    @Query("""
        SELECT r.driver 
        FROM Reservation r
        WHERE (:agentId IS NULL OR r.agent.id = :agentId)
        GROUP BY r.driver
        ORDER BY MAX(r.createdAt) DESC
        LIMIT 5
            """)
    List<User> findLatestDrivers(@Param("agentId") UUID agentId);

    Long countByAgentId(@Param("agentId") UUID agentId);

    @Query("""
        SELECT r.agent.firstName, r.agent.lastName, COUNT(r) 
        FROM Reservation r 
        GROUP BY r.agent
        """)
    List<Object[]> countReservationsByAgent();

    Long countByAgentIdAndReturnedAtIsNull(@Param("agentId") UUID agentId);

    Long countByAgentIdAndStartDateBetween(UUID agentId, OffsetDateTime start, OffsetDateTime end);

}
