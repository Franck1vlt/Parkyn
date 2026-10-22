package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.model.Vehicle.VehicleCondition;
import app.parkyn.model.Vehicle.VehicleStatus;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc 
@IntegrationTest 
public class ReservationScopeTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ObjectMapper objectMapper;

    private static final String TEST_DRIVER_A = "driverA@parkyn.app";
    private static final String TEST_DRIVER_B = "driverB@parkyn.app";
    private static final String TEST_AGENT = "agent@parkyn.app";
    private static final String TEST_ADMIN = "admin@parkyn.app";
    private static final String PASSWORD = "Password123!";
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-02T10:00:00Z");
    private String driverAId;
    private Reservation r2;

    @BeforeEach 
    void setUp() {
        TestUsers testUsers = new TestUsers(userRepository, jdbcTemplate, passwordEncoder);

        // Users
        User driverA = testUsers.create(TEST_DRIVER_A, PASSWORD, User.Role.DRIVER);
        User driverB = testUsers.create(TEST_DRIVER_B, PASSWORD, User.Role.DRIVER);
        User agent = testUsers.create(TEST_AGENT, PASSWORD, User.Role.AGENT);
        User admin = testUsers.create(TEST_ADMIN, PASSWORD, User.Role.ADMIN);
        driverAId = driverA.getId().toString();

        // Véhicule
        Vehicle car = new Vehicle();
        car.setPlateNumber("AA-123-BB");
        car.setBrand("Toyota");
        car.setModel("Yaris");
        car.setYear(2009);
        car.setMileageKM(135000);
        car.setCondition(VehicleCondition.GOOD);
        car.setStatus(VehicleStatus.AVAILABLE);
        car.setCommissionedAt(null);
        car.setDeletedAt(null);
        car.setCreatedAt(T0);
        vehicleRepository.saveAndFlush(car);

        // Réservation
        Reservation r1 = new Reservation();
        r1.setVehicle(car);
        r1.setDriver(driverA);
        r1.setAgent(agent);
        r1.setStartDate(T0.plusDays(1));
        r1.setEndDate(T0.plusDays(2));
        r1.setConditionBefore(Reservation.Condition.FAIR);
        r1.setMileageStart(135000);
        r1.setCreatedAt(T0);
        reservationRepository.saveAndFlush(r1);

        r2 = new Reservation();
        r2.setVehicle(car);
        r2.setDriver(driverB);
        r2.setAgent(agent);
        r2.setStartDate(T0.plusDays(3));
        r2.setEndDate(T0.plusDays(4));
        r2.setConditionBefore(Reservation.Condition.FAIR);
        r2.setMileageStart(135000);
        r2.setCreatedAt(T0);
        reservationRepository.saveAndFlush(r2);
    }

    // Driver A liste les réservations : il ne voit que la sienne
    @Test
    void driverAOnlySeesTheirReservation() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].driverId").value(driverAId.toString()));
    }

    // Driver A demande la réservation de Driver B : il reçoit 404
    @Test 
    void driverACannotAccessDriverBReservation() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/" + r2.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // Driver A demande un id inexistant : il reçoit 404
    @Test
    void driverAReceives404ForNonExistentReservation() throws Exception {
        MvcResult nonExistentReservation = mockMvc.perform(
            get("/api/v1/reservations/00000000-0000-0000-0000-000000000000")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
            .andReturn();

        MvcResult driverBReservation = mockMvc.perform(
            get("/api/v1/reservations/" + r2.getId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
            .andReturn();

        assertEquals(404, nonExistentReservation.getResponse().getStatus());
        assertEquals(nonExistentReservation.getResponse().getStatus(),
            driverBReservation.getResponse().getStatus());
        assertEquals(nonExistentReservation.getResponse().getContentAsString(),
            driverBReservation.getResponse().getContentAsString());
    }

    // Agent liste les réservations : il voit les deux réservations
    @Test
    void agentSeesAllReservations() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_AGENT, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
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

}
