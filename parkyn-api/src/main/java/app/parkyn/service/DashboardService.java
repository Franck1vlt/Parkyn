package app.parkyn.service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.parkyn.dto.DashboardResponse;
import app.parkyn.dto.DashboardResponse.AgentStat;
import app.parkyn.dto.DashboardResponse.MyStats;
import app.parkyn.model.User;
import app.parkyn.model.User.Role;
import app.parkyn.model.Vehicle.VehicleStatus;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import app.parkyn.dto.ReservationResponse;
import app.parkyn.dto.UserResponse;
import app.parkyn.dto.VehicleResponse;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional
    private List<ReservationResponse> getLatestReservations(User user){
        boolean isAdmin = user.getRole() == Role.ADMIN;
        var latestReservations = isAdmin ? reservationRepository.findTop5ByOrderByCreatedAtDesc(): reservationRepository.findTop5ByAgentIdOrderByCreatedAtDesc(user.getId());
        return latestReservations.stream().map(ReservationResponse::from).toList();
    }

    @Transactional
    private List<VehicleResponse> getVehiclesInMaintenance(){
        // En maintenance : le statut stocké l'emporte sur le calcul, qu'il soit dehors ou non
        return vehicleRepository.findByStatusAndDeletedAtIsNull(VehicleStatus.MAINTENANCE).stream()
            .map(vehicle -> VehicleResponse.from(vehicle, false)).toList();
    }

    @Transactional
    private List<UserResponse> getLatestDrivers(User user){
        UUID agentId = user.getRole() == Role.AGENT ? user.getId() : null;
        return reservationRepository.findLatestDrivers(agentId)
            .stream().map(UserResponse::from).toList();
    }

    @Transactional
    private List<AgentStat> getReservationsByAgent(){
        var results = reservationRepository.countReservationsByAgent();
        return results.stream()
            .map(row -> new AgentStat(row[0] + " " + row[1], (long) row[2]))
            .toList();
    }

    @Transactional
    private MyStats getMyStats(User user){
        long total = reservationRepository.countByAgentIdAndCancelledAtIsNull(user.getId());
        long actives = reservationRepository.countByAgentIdAndReturnedAtIsNullAndCancelledAtIsNull(user.getId());
        OffsetDateTime startOfThisMonth = OffsetDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);
        OffsetDateTime endOfLastMonth = startOfThisMonth;
        long thisMonth = reservationRepository.countByAgentIdAndCancelledAtIsNullAndStartDateBetween(user.getId(), startOfThisMonth, OffsetDateTime.now());
        long lastMonth = reservationRepository.countByAgentIdAndCancelledAtIsNullAndStartDateBetween(user.getId(), startOfLastMonth, endOfLastMonth);
        return new MyStats(total, actives, thisMonth, lastMonth);
    }

    @Transactional
    public DashboardResponse getDashboard(User user){
        boolean isAdmin = user.getRole() == Role.ADMIN;
        var latestReservations = getLatestReservations(user);
        var vehiclesInMaintenance = getVehiclesInMaintenance();
        var latestDrivers = getLatestDrivers(user);
        var reservationsByAgent = isAdmin ? getReservationsByAgent() : null;
        var myStats = user.getRole() == Role.AGENT ? getMyStats(user) : null;
        return new DashboardResponse(
            latestReservations,
            vehiclesInMaintenance,
            latestDrivers,
            reservationsByAgent,
            myStats
        );
    }

}
