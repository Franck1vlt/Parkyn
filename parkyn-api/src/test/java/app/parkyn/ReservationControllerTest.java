package app.parkyn;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import tools.jackson.databind.ObjectMapper;

import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public class ReservationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired VehicleRepository vehicleRepository;

    private static final String TEST_EMAIL = "test@parkyn.app";
    private static final String TEST_PASSWORD = "Password123!";
    private String testRefreshToken;
    private UUID testVehicleId;
    private UUID testReservationId;


    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(TEST_EMAIL);
        user.setHashedPwd(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(User.Role.ADMIN);
        user.setActive(true);
        userRepository.saveAndFlush(user);

        testRefreshToken = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(testRefreshToken);
        rt.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(rt);

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("AB-123-CD");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        vehicle.setYear(2015);
        vehicleRepository.saveAndFlush(vehicle);

        Reservation reservation = new Reservation();
        reservation.setVehicle(vehicle);
        reservation.setDriver(user);
        reservation.setAgent(user);
        reservation.setStartDate(OffsetDateTime.now().plusDays(1));
        reservation.setEndDate(OffsetDateTime.now().plusDays(3));
        reservation.setConditionBefore(Reservation.Condition.GOOD);
        reservation.setMileageStart(45650);
        reservationRepository.saveAndFlush(reservation);

        testVehicleId = vehicle.getId();
        testReservationId = reservation.getId();
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        vehicleRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
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
            .andExpect(jsonPath("$.content[0].conditionBefore").value("GOOD"))
            .andExpect(jsonPath("$.content[0].mileageStart").value(45650))
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
            .andExpect(jsonPath("$.vehicleId").isString())
            .andExpect(jsonPath("$.driverId").isString())
            .andExpect(jsonPath("$.agentId").isString())
            .andExpect(jsonPath("$.startDate").isString())
            .andExpect(jsonPath("$.endDate").isString())
            .andExpect(jsonPath("$.conditionBefore").value("GOOD"))
            .andExpect(jsonPath("$.mileageStart").isNumber())
            .andExpect(jsonPath("$.returnedAt").doesNotExist())
            .andExpect(jsonPath("$.conditionAfter").doesNotExist())
            .andExpect(jsonPath("$.mileageEnd").doesNotExist())
            .andExpect(jsonPath("$.damageReported").doesNotExist())
            .andExpect(jsonPath("$.damageDetails").doesNotExist())
            .andExpect(jsonPath("$.notesAfter").doesNotExist())
            .andExpect(status().isCreated());
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
            .andExpect(jsonPath("$.notesAfter").isString())
            .andExpect(status().isOk());
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
