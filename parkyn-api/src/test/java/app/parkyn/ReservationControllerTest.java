package app.parkyn;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import tools.jackson.databind.ObjectMapper;

import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.model.Vehicle.VehicleCondition;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;
import app.parkyn.tenant.Tenants;

@AutoConfigureMockMvc
@IntegrationTest
public class ReservationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL = "test@parkyn.app";
    private static final String TEST_PASSWORD = "Password123!";
    private String testRefreshToken;
    private UUID testUserId;
    private UUID testVehicleId;
    private UUID testReservationId;


    @BeforeEach
    void setUp() {
        User user = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(TEST_EMAIL, TEST_PASSWORD, User.Role.ADMIN);
        testUserId = user.getId();

        testRefreshToken = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(testRefreshToken);
        rt.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(rt);

        Vehicle vehicle = new Vehicle();

        vehicle.setTenantId(Tenants.DEFAULT_ID);
        vehicle.setPlateNumber("AB-123-CD");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        vehicle.setCondition(VehicleCondition.GOOD);
        vehicle.setYear(2015);
        vehicleRepository.saveAndFlush(vehicle);

        Reservation reservation = new Reservation();

        reservation.setTenantId(Tenants.DEFAULT_ID);
        reservation.setVehicle(vehicle);
        reservation.setDriver(user);
        reservation.setAgent(user);
        reservation.setStartDate(OffsetDateTime.now().plusDays(1));
        reservation.setEndDate(OffsetDateTime.now().plusDays(3));
        reservation.setConditionBefore(Reservation.Condition.GOOD);
        reservation.setMileageStart(45650);
        reservation.setCreatedBy(user);
        reservationRepository.saveAndFlush(reservation);
        jdbcTemplate.update(
            "UPDATE reservations SET updated_at = '2020-01-01T00:00:00Z' WHERE id = ?",
            reservation.getId()
        );

        testVehicleId = vehicle.getId();
        testReservationId = reservation.getId();
    }

    @Test
    public void testGetReservations() throws Exception {
        String token = getAccessToken();

        mockMvc.perform(get("/api/v1/reservations")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].vehicleId").isString())
            .andExpect(jsonPath("$.content[0].driverId").isString())
            .andExpect(jsonPath("$.content[0].agentId").isString())
            .andExpect(jsonPath("$.content[0].startDate").isString())
            .andExpect(jsonPath("$.content[0].endDate").isString())
            .andExpect(jsonPath("$.content[0].conditionBefore").isString())
            .andExpect(jsonPath("$.content[0].mileageStart").isNumber())
            .andExpect(jsonPath("$.content[0].returnedAt").doesNotExist())
            .andExpect(jsonPath("$.content[0].conditionAfter").doesNotExist())
            .andExpect(jsonPath("$.content[0].mileageEnd").doesNotExist())
            .andExpect(jsonPath("$.content[0].damageReported").doesNotExist())
            .andExpect(jsonPath("$.content[0].damageDetails").doesNotExist())
            .andExpect(jsonPath("$.content[0].notesAfter").doesNotExist())
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    public void testAddReservation() throws Exception {
        String token = getAccessToken();
        ReservationRequest request = new ReservationRequest(
            testVehicleId,
            null,
            "Jean",
            "Dupont",
            "jean.dupont@parkyn.app",
            null,
            OffsetDateTime.now().plusDays(5),
            OffsetDateTime.now().plusDays(7),
            Reservation.Condition.GOOD,
            50000,
            null
        );
        
        mockMvc.perform(post("/api/v1/reservations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.vehicleId").value(testVehicleId.toString()))
            // Le conducteur est Jean Dupont, créé par la requête ; l'agent est l'utilisateur connecté
            .andExpect(jsonPath("$.driverId").value(driverIdOf("jean.dupont@parkyn.app")))
            .andExpect(jsonPath("$.agentId").value(testUserId.toString()))
            .andExpect(jsonPath("$.startDate").isString())
            .andExpect(jsonPath("$.endDate").isString())
            .andExpect(jsonPath("$.conditionBefore").isString())
            .andExpect(jsonPath("$.mileageStart").isNumber())
            .andExpect(jsonPath("$.returnedAt").doesNotExist())
            .andExpect(jsonPath("$.conditionAfter").doesNotExist())
            .andExpect(jsonPath("$.mileageEnd").doesNotExist())
            .andExpect(jsonPath("$.damageReported").doesNotExist())
            .andExpect(jsonPath("$.damageDetails").doesNotExist())
            .andExpect(jsonPath("$.notesAfter").doesNotExist());

        Reservation createdReservation = reservationRepository.findAll().stream()
            .filter(reservation -> !reservation.getId().equals(testReservationId))
            .findFirst()
            .orElseThrow();
        assertNotNull(createdReservation.getCreatedBy());
        assertEquals(testUserId, createdReservation.getCreatedBy().getId());
    }


    @Test
    public void testGetReservationById() throws Exception {
        String token = getAccessToken();
        
        mockMvc.perform(get("/api/v1/reservations/" + testReservationId)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vehicleId").isString())
            .andExpect(jsonPath("$.driverId").isString())
            .andExpect(jsonPath("$.agentId").isString())
            .andExpect(jsonPath("$.startDate").isString())
            .andExpect(jsonPath("$.endDate").isString())
            .andExpect(jsonPath("$.conditionBefore").isString())
            .andExpect(jsonPath("$.mileageStart").isNumber())
            .andExpect(jsonPath("$.returnedAt").doesNotExist())
            .andExpect(jsonPath("$.conditionAfter").doesNotExist())
            .andExpect(jsonPath("$.mileageEnd").doesNotExist())
            .andExpect(jsonPath("$.damageReported").doesNotExist())
            .andExpect(jsonPath("$.damageDetails").doesNotExist())
            .andExpect(jsonPath("$.notesAfter").doesNotExist());
    }

    
    @Test
    public void testReturnVehicle() throws Exception {
        String token = getAccessToken();
        ReturnVehicleRequest request = new ReturnVehicleRequest(
            Reservation.Condition.GOOD, 
            50256, 
            true, 
            "Rayure sur le pare-choc arrière", 
            "Carburant à moitié"
        );
        
        mockMvc.perform(post("/api/v1/reservations/" + testReservationId + "/return")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vehicleId").isString())
            .andExpect(jsonPath("$.driverId").isString())
            .andExpect(jsonPath("$.agentId").isString())
            .andExpect(jsonPath("$.startDate").isString())
            .andExpect(jsonPath("$.endDate").isString())
            .andExpect(jsonPath("$.conditionBefore").isString())
            .andExpect(jsonPath("$.mileageStart").isNumber())
            .andExpect(jsonPath("$.returnedAt").isNotEmpty())
            .andExpect(jsonPath("$.conditionAfter").isString())
            .andExpect(jsonPath("$.mileageEnd").isNumber())
            .andExpect(jsonPath("$.damageReported").isBoolean())
            .andExpect(jsonPath("$.damageDetails").isString())
            .andExpect(jsonPath("$.notesAfter").isString());

        Reservation returnedReservation = reservationRepository.findById(testReservationId).orElseThrow();
        assertNotNull(returnedReservation.getCreatedBy());
        assertEquals(testUserId, returnedReservation.getCreatedBy().getId());
        assertNotNull(returnedReservation.getUpdatedBy());
        assertEquals(testUserId, returnedReservation.getUpdatedBy().getId());
        assertNotNull(returnedReservation.getCreatedAt());
        assertNotNull(returnedReservation.getUpdatedAt());
        assertTrue(returnedReservation.getUpdatedAt().isAfter(OffsetDateTime.parse("2020-01-01T00:00:00Z")));
    }


    // L'id (texte) du conducteur portant cet email, lu en base après sa création
    private String driverIdOf(String email) {
        UUID driverId = userRepository.findByEmail(User.normalizeEmail(email)).orElseThrow().getId();
        assertNotEquals(testUserId, driverId);
        return driverId.toString();
    }

    private String getAccessToken() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, LoginResponse.class).accessToken();
    }
}
