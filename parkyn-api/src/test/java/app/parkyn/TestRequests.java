package app.parkyn;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import app.parkyn.dto.RegisterRequest;
import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.dto.UserRequest;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.dto.VehicleRequest;
import app.parkyn.dto.VehicleStatusRequest;
import app.parkyn.dto.VehicleUpdateRequest;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import jakarta.servlet.http.Cookie;

/**
 * Corps de requêtes valides pour les tests : ils passent la validation @Valid,
 * pour que les tests mesurent la sécurité et non un 400.
 */
public final class TestRequests {

    private TestRequests() {
        // classe utilitaire : uniquement des méthodes static, jamais instanciée
    }

    // les méthodes validXxx() déplacées depuis AuthorizationMatrixTest
    private static final UUID RANDOM_UUID = UUID.randomUUID();

    public static ReservationRequest validReservationRequest() {
        return new ReservationRequest(
            RANDOM_UUID,
            null,
            "Jean",
            "Dupont",
            "jean.dupont@parkyn.app",
            null,
            OffsetDateTime.now().plusYears(4),
            OffsetDateTime.now().plusYears(4).plusDays(7),
            Reservation.Condition.GOOD,
            50000,
            null
        );
    }

    public static ReturnVehicleRequest validReturnVehicleRequest() {
        return new ReturnVehicleRequest(
            Reservation.Condition.GOOD, 
            50256, 
            true, 
            "Rayure sur le pare-choc arrière", 
            "Carburant à moitié"
        );
    }

    public static UserRequest validUserRequest() {
        return new UserRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.DRIVER,
            true
        );
    }

    public static UserUpdateRequest validUserUpdateRequest() {
        return new UserUpdateRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.DRIVER,
            true
        );
    }

    public static VehicleRequest validVehicleRequest() {
        return new VehicleRequest(
            "AA-123-AA", 
            "Renault", 
            "Clio", 
            2020, 
            50000, 
            Vehicle.VehicleCondition.GOOD, 
            LocalDate.of(2020, 1, 15)
        );
    }

    public static VehicleUpdateRequest validVehicleUpdateRequest() {
        return new VehicleUpdateRequest(
            null, 
            null, 
            null, 
            2025, 
            12500,
            Vehicle.VehicleCondition.EXCELLENT,
            null
        );
    }

    // --- Helpers ---

    public static VehicleStatusRequest validVehicleStatusRequest() {
        return new VehicleStatusRequest(Vehicle.VehicleStatus.MAINTENANCE);
    }

    public static RegisterRequest validRegisterRequest() {
        return new RegisterRequest(
            "Jean", 
            "Dupont", 
            "jean.dupont@parkyn.app", 
            "Password123!"
        );
    }

    public static RequestPostProcessor csrfToken() {
        return request -> {
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] cookies = Arrays.copyOf(existing, existing.length + 1);
            cookies[existing.length] = new Cookie("XSRF-TOKEN", "test-csrf-token");
            request.setCookies(cookies);
            request.addHeader("X-XSRF-TOKEN", "test-csrf-token");
            return request;
        };
    }
}
