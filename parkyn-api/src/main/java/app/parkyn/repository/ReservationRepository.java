package app.parkyn.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    @EntityGraph(attributePaths = {"driver", "agent", "vehicle"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    // Verrou sur la réservation jusqu'à la fin de la transaction (FOR NO KEY UPDATE) : modification,
    // annulation et retour d'une même réservation passent l'une après l'autre, chacune sur l'état à jour.
    // Ordre des verrous : la réservation, puis le véhicule (évite les blocages mutuels)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    // Règle unique des créneaux occupés, identique à la contrainte reservations_no_overlap (V14) :
    // une réservation occupe [début, fin[, ou [début, retour[ si le véhicule est rendu en avance.
    // Une réservation annulée n'occupe rien. vehicleId facultatif : null = tous les véhicules du tenant.
    // excludeId facultatif : la réservation en cours de modification ne se gêne pas elle-même.
    @Query("""
        SELECT DISTINCT r.vehicle.id FROM Reservation r
        WHERE (:vehicleId IS NULL OR r.vehicle.id = :vehicleId)
        AND (:excludeId IS NULL OR r.id <> :excludeId)
        AND r.cancelledAt IS NULL
        AND r.startDate < :endDate
        AND (r.returnedAt IS NULL OR r.returnedAt > r.startDate)
        AND (CASE WHEN r.returnedAt IS NOT NULL AND r.returnedAt < r.endDate THEN r.returnedAt ELSE r.endDate END) > :startDate
        """)
    Set<UUID> findVehicleIdsBookedBetween(
        @Param("vehicleId") UUID vehicleId,
        @Param("excludeId") UUID excludeId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate
    );

    default boolean hasConflict(UUID vehicleId, OffsetDateTime startDate, OffsetDateTime endDate) {
        return hasConflict(vehicleId, startDate, endDate, null);
    }

    default boolean hasConflict(UUID vehicleId, OffsetDateTime startDate, OffsetDateTime endDate, UUID excludeId) {
        return !findVehicleIdsBookedBetween(vehicleId, excludeId, startDate, endDate).isEmpty();
    }

    // Véhicules dehors à cet instant : réservation commencée et pas encore retournée (même après sa fin, en retard).
    // vehicleId facultatif : null = tous les véhicules du tenant.
    @Query("""
        SELECT DISTINCT r.vehicle.id FROM Reservation r
        WHERE (:vehicleId IS NULL OR r.vehicle.id = :vehicleId)
        AND r.returnedAt IS NULL
        AND r.cancelledAt IS NULL
        AND r.startDate <= :now
        """)
    Set<UUID> findVehicleIdsInUseAt(@Param("vehicleId") UUID vehicleId, @Param("now") OffsetDateTime now);

    List<Reservation> findByReturnedAtIsNullAndCancelledAtIsNullAndEndDateBetween(OffsetDateTime start, OffsetDateTime end);

    // Appelée hors connexion (traitement planifié) : sans filtre, elle liste les tenants de toutes les entreprises
    @Query("SELECT DISTINCT r.tenantId FROM Reservation r WHERE r.returnedAt IS NULL AND r.cancelledAt IS NULL")
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

    // Compteurs du tableau de bord : les réservations annulées ne comptent pas
    Long countByAgentIdAndCancelledAtIsNull(@Param("agentId") UUID agentId);

    @Query("""
        SELECT r.agent.firstName, r.agent.lastName, COUNT(r) 
        FROM Reservation r
        WHERE r.cancelledAt IS NULL
        GROUP BY r.agent
        """)
    List<Object[]> countReservationsByAgent();

    Long countByAgentIdAndReturnedAtIsNullAndCancelledAtIsNull(@Param("agentId") UUID agentId);

    Long countByAgentIdAndCancelledAtIsNullAndStartDateBetween(UUID agentId, OffsetDateTime start, OffsetDateTime end);

}
