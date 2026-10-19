package app.parkyn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;
import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.RefreshRequest;
import app.parkyn.dto.RegisterRequest;
import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.dto.UserRequest;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.dto.VehicleRequest;
import app.parkyn.dto.VehicleStatusRequest;
import app.parkyn.dto.VehicleUpdateRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.JwtService;

import static app.parkyn.AuthorizationMatrixTest.Expected.*;

@AutoConfigureMockMvc 
@IntegrationTest
class AuthorizationMatrixTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    enum Actor { ADMIN, AGENT, DRIVER, ANONYMOUS }

    enum Expected { ALLOWED, DENIED }

    record MatrixCase(Actor actor, HttpMethod method, String path, Object body, Expected expected) {
        @Override
        public String toString() {
            return String.format("%-9s %-6s %s", actor, method, path);
        }
    }

    static Stream<MatrixCase> row(HttpMethod method, String path, Object body, Expected admin, Expected agent, Expected driver, Expected anonymous) {
        return Stream.of(
            new MatrixCase(Actor.ADMIN, method, path, body, admin),
            new MatrixCase(Actor.AGENT, method, path, body, agent),
            new MatrixCase(Actor.DRIVER, method, path, body, driver),
            new MatrixCase(Actor.ANONYMOUS, method, path, body, anonymous)
        );
    }

    private final Map<Actor, String> tokens = new EnumMap<>(Actor.class);
    private static final String RANDOM_ID = UUID.randomUUID().toString();
    private static final UUID RANDOM_UUID = UUID.randomUUID();
    private static final String LOGIN_EMAIL = "admin@parkyn.app";
    private static final String LOGIN_PASSWORD = "Password123!";
    private static final String REFRESH_TOKEN = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        User[] adminUser = new User[1];
        Map.of(
            Actor.ADMIN, new Object[] { "admin@parkyn.app", User.Role.ADMIN },
            Actor.AGENT, new Object[] { "agent@parkyn.app", User.Role.AGENT },
            Actor.DRIVER, new Object[] { "driver@parkyn.app", User.Role.DRIVER }
        ).forEach((actor, values) -> {
            String email = (String) values[0];
            User.Role role = (User.Role) values[1];
            User user = new User();
            user.setFirstName("Test");
            user.setLastName(role.name());
            user.setEmail(email);
            user.setHashedPwd(email.equals(LOGIN_EMAIL)
                ? passwordEncoder.encode(LOGIN_PASSWORD)
                : "not-used");
            user.setRole(role);
            user.setActive(true);
            User savedUser = userRepository.saveAndFlush(user);
            tokens.put(actor, jwtService.generateToken(savedUser));
            if (role == User.Role.ADMIN) {
                adminUser[0] = savedUser;
            }
        });

        RefreshToken rt = new RefreshToken();
        rt.setUser(adminUser[0]);
        rt.setToken(REFRESH_TOKEN);
        rt.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(rt);
    }

    static LoginRequest validLoginRequest() {
        return new LoginRequest(LOGIN_EMAIL, LOGIN_PASSWORD);
    }

    static RefreshRequest validRefreshRequest() {
        return new RefreshRequest(
            REFRESH_TOKEN
        );
    }

    static RegisterRequest validRegisterRequest() {
        return new RegisterRequest(
            "Jean", 
            "Dupont", 
            "jean.dupont@parkyn.app", 
            "Password123!"
        );
    }

    static ReservationRequest validReservationRequest() {
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

    static ReturnVehicleRequest validReturnVehicleRequest() {
        return new ReturnVehicleRequest(
            Reservation.Condition.GOOD, 
            50256, 
            true, 
            "Rayure sur le pare-choc arrière", 
            "Carburant à moitié"
        );
    }

    static UserRequest validUserRequest() {
        return new UserRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.DRIVER,
            true
        );
    }

    static UserUpdateRequest validUserUpdateRequest() {
        return new UserUpdateRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.DRIVER,
            true
        );
    }

    static VehicleRequest validVehicleRequest() {
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

    static VehicleUpdateRequest validVehicleUpdateRequest() {
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

    static VehicleStatusRequest validVehicleStatusRequest() {
        return new VehicleStatusRequest(Vehicle.VehicleStatus.RESERVED);
    }


    static Stream<MatrixCase> matrix() {
        return Stream.of(
            // Endpoints d'Authentification
            row(HttpMethod.POST, "/api/v1/auth/login", validLoginRequest(), ALLOWED, ALLOWED, ALLOWED, ALLOWED),
            row(HttpMethod.POST, "/api/v1/auth/refresh", validRefreshRequest(), ALLOWED, ALLOWED, ALLOWED, ALLOWED),
            row(HttpMethod.POST, "/api/v1/auth/register", validRegisterRequest(), ALLOWED, ALLOWED, ALLOWED, ALLOWED),
            row(HttpMethod.POST, "/api/v1/auth/logout", null, ALLOWED, ALLOWED, ALLOWED, DENIED),
            row(HttpMethod.GET, "/api/v1/auth/me", null, ALLOWED, ALLOWED, ALLOWED, DENIED),

            // Endpoint de Dashboard
            row(HttpMethod.GET, "/api/v1/dashboard", null, ALLOWED, ALLOWED, DENIED, DENIED),

            // Endpoints de notifications
            row(HttpMethod.GET, "/api/v1/notifications", null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.PATCH, "/api/v1/notifications/" + RANDOM_ID + "/read", null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.PATCH, "/api/v1/notifications/read-all", null, ALLOWED, ALLOWED, DENIED, DENIED),

            // Endpoints de réservations
            row(HttpMethod.GET, "/api/v1/reservations", null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.POST, "/api/v1/reservations", validReservationRequest(), ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.GET, "/api/v1/reservations/" + RANDOM_ID, null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.POST, "/api/v1/reservations/" + RANDOM_ID +"/return", validReturnVehicleRequest(), ALLOWED, ALLOWED, DENIED, DENIED),

            // Endpoints d'utilisateurs
            row(HttpMethod.GET, "/api/v1/users", null, ALLOWED, DENIED, DENIED, DENIED),
            row(HttpMethod.POST, "/api/v1/users", validUserRequest(), ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.GET, "/api/v1/users/" + RANDOM_ID, null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.PATCH, "/api/v1/users/" + RANDOM_ID, validUserUpdateRequest(), ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.DELETE, "/api/v1/users/" + RANDOM_ID, null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.PATCH, "/api/v1/users/" + RANDOM_ID + "/reactivate", null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.GET, "/api/v1/users/drivers", null, ALLOWED, ALLOWED, DENIED, DENIED),

            // Endpoints de véhicules
            row(HttpMethod.GET, "/api/v1/vehicles", null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.POST, "/api/v1/vehicles", validVehicleRequest(), ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.GET, "/api/v1/vehicles/" + RANDOM_ID, null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.PATCH, "/api/v1/vehicles/" + RANDOM_ID, validVehicleUpdateRequest(), ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.DELETE, "/api/v1/vehicles/" + RANDOM_ID, null, ALLOWED, ALLOWED, DENIED, DENIED),
            row(HttpMethod.PATCH, "/api/v1/vehicles/" + RANDOM_ID + "/status", validVehicleStatusRequest(), ALLOWED, ALLOWED, DENIED, DENIED)
        ).flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void matrixCase(MatrixCase c) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = request(c.method(), c.path());

        if (c.body() != null) {
            requestBuilder.contentType("application/json");
            requestBuilder.content(objectMapper.writeValueAsString(c.body()));
        }

        if (c.actor() != Actor.ANONYMOUS) {
            String token = tokens.get(c.actor());
            requestBuilder.header("Authorization", "Bearer " + token);
        }
        
        MockHttpServletResponse res = mockMvc.perform(requestBuilder).andReturn().getResponse();
        int status = res.getStatus();
        
        if (c.expected() == Expected.DENIED) {
            assertEquals(403, status, "Refus attendu (403)");
        } else {
            assertTrue(
                !Set.of(400, 401, 403).contains(status),
                "Accès attendu, mais statut " + status
                    + " (400 = corps de test invalide, 401/403 = accès refusé)"
            );
        }
    }
}
