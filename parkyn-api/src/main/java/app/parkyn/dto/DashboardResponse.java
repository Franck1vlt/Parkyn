package app.parkyn.dto;

import java.util.List;

public record DashboardResponse(
    List<ReservationResponse> latestReservations,
    List<VehicleResponse> vehiclesInMaintenance,
    List<UserResponse> latestDrivers,
    List<AgentStat> reservationsByAgent,
    MyStats myStats
) {
    public record AgentStat(String agentName, long count) {}
    public record MyStats(long totalReservations, long activeReservations, long thisMonthReservations, long lastMonthReservations) {}
}

