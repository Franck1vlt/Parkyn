package app.parkyn.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

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

    List<Reservation> findByReturnedAtIsNullAndEndDateBetween(OffsetDateTime start, OffsetDateTime end);

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
