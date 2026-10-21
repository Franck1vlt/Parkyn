package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import app.parkyn.IntegrationTest;
import app.parkyn.TestRequests;
import app.parkyn.TestUsers;
import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.UserRequest;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;

@AutoConfigureMockMvc
@IntegrationTest
class RoleAssignmentTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@parkyn.app";
    private static final String AGENT_EMAIL = "agent@parkyn.app";
    private static final String DRIVER_EMAIL = "driver@parkyn.app";

    private UUID driverId;     // le DRIVER à promouvoir (scénario 3)
    private UUID vehicleId;    // un véhicule réel (scénario 4)

    @BeforeEach
    void setUp() {
        TestUsers users = new TestUsers(userRepository, jdbcTemplate, passwordEncoder);
        User admin = users.create(ADMIN_EMAIL, PASSWORD, User.Role.ADMIN);
        User agent = users.create(AGENT_EMAIL, PASSWORD, User.Role.AGENT);
        User driver = users.create(DRIVER_EMAIL, PASSWORD, User.Role.DRIVER);
        driverId = driver.getId();

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("ABC-123");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Corolla");
        vehicle.setMileageKM(4000);
        vehicleRepository.saveAndFlush(vehicle);
        vehicleId = vehicle.getId();
    }

    // Scénario 1 : un compte créé via l'API reçoit les permissions de son rôle
    @Test
    void agentCreatedThroughApiCanAccessReservations() throws Exception {
        UserRequest userRequest = new UserRequest("John", "Doe", "john.doe@example.com", PASSWORD, User.Role.AGENT, true);
        mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(ADMIN_EMAIL, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());
        String agentToken = login("john.doe@example.com", PASSWORD);
        mockMvc.perform(get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
            .andExpect(status().isOk());
    }

    // Scénario 2 : l'inscription donne un rôle en base
    @Test
    void registeredAccountHasItsRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TestRequests.validRegisterRequest()))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated());
        String newEmail = TestRequests.validRegisterRequest().email();
        String normalizedEmail = User.normalizeEmail(newEmail);
        UUID newUserId = userRepository.findByEmail(normalizedEmail).orElseThrow().getId();
        List<String> roles = roleNamesOf(newUserId);
        assertEquals(1, roles.size());
        assertEquals(List.of(User.Role.DRIVER.name()), roles);
    }

    // Scénario 3 : un changement de rôle remplace l'ancien rôle
    @Test
    void roleChangeReplacesPreviousRole() throws Exception {
        UserUpdateRequest userUpdateRequest = new UserUpdateRequest(null, null, null, null, User.Role.AGENT, true);
        mockMvc.perform(patch("/api/v1/users/" + driverId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(ADMIN_EMAIL, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userUpdateRequest))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        List<String> roles = roleNamesOf(driverId);
        assertEquals(1, roles.size());
        assertEquals(List.of(User.Role.AGENT.name()), roles);
    }

    // Scénario 4 : un conducteur créé lors d'une réservation reçoit le rôle DRIVER
    @Test
    void driverCreatedByReservationHasDriverRole() throws Exception {
        String driverEmail = "john.doe@example.com";
        ReservationRequest reservationRequest = new ReservationRequest(
            vehicleId,
            null,
            "John",
            "Doe",
            driverEmail,
            null,
            OffsetDateTime.now().plusYears(2),
            OffsetDateTime.now().plusYears(2).plusDays(7),
            TestRequests.validReservationRequest().conditionBefore(),
            TestRequests.validReservationRequest().mileageStart(),
            null
        );
        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(AGENT_EMAIL, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reservationRequest))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getErrorMessage();
        String normalizedEmail = User.normalizeEmail(driverEmail);
        UUID newDriverId = userRepository.findByEmail(normalizedEmail).orElseThrow().getId();
        List<String> roles = roleNamesOf(newDriverId);
        assertEquals(1, roles.size());
        assertEquals(List.of(User.Role.DRIVER.name()), roles);
    }

    // --- Aides ---

    // Se connecte via /auth/login et renvoie l'access token
    private String login(String email, String password) throws Exception {
        // même modèle que getAccessToken(...) dans tes tests de contrôleurs
        LoginRequest request = new LoginRequest(email, password);
        String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, LoginResponse.class).accessToken();
    }

    // Les noms des rôles d'un utilisateur, lus directement dans user_roles
    private List<String> roleNamesOf(UUID userId) {
        return jdbcTemplate.queryForList(
            "SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?",
            String.class, userId);
    }
}
