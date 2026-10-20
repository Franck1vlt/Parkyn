package app.parkyn;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import tools.jackson.databind.ObjectMapper;

import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.VehicleRequest;
import app.parkyn.dto.VehicleStatusRequest;
import app.parkyn.dto.VehicleUpdateRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;

@AutoConfigureMockMvc
@IntegrationTest
public class VehicleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL = "test@parkyn.app";
    private static final String TEST_PASSWORD = "Password123!";
    private String testRefreshToken;
    private UUID testVehicleId;

    @BeforeEach
    void setUp() {
        User user = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(TEST_EMAIL, TEST_PASSWORD, User.Role.ADMIN);

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
        vehicle.setCommissionedAt(LocalDate.of(2015, 1, 1));
        vehicle.setCreatedAt(OffsetDateTime.now());
        vehicleRepository.saveAndFlush(vehicle);

        testVehicleId = vehicle.getId();
    }

    @Test
    public void testGetVehicles() throws Exception {
        String token = getAccessToken();

        mockMvc.perform(get("/api/v1/vehicles")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].plateNumber").isString())
            .andExpect(jsonPath("$.content[0].brand").isString())
            .andExpect(jsonPath("$.content[0].model").isString())
            .andExpect(jsonPath("$.content[0].year").isNumber())
            .andExpect(jsonPath("$.content[0].mileageKM").isNumber())
            .andExpect(jsonPath("$.content[0].status").isString())
            .andExpect(jsonPath("$.content[0].commissionedAt").isString())
            .andExpect(jsonPath("$.content[0].createdAt").isString())
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    public void testAddVehicle() throws Exception {
        String token = getAccessToken();
        VehicleRequest request = new VehicleRequest(
            "EF-456-IJ",
            "Renault",
            "Zoe",
            2023,
            21345,
            Vehicle.VehicleCondition.GOOD,
            LocalDate.of(2026, 4, 21)
        );

        mockMvc.perform(post("/api/v1/vehicles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.plateNumber").isString())
            .andExpect(jsonPath("$.brand").isString())
            .andExpect(jsonPath("$.model").isString())
            .andExpect(jsonPath("$.year").isNumber())
            .andExpect(jsonPath("$.mileageKM").isNumber())
            .andExpect(jsonPath("$.status").isString())
            .andExpect(jsonPath("$.commissionedAt").isString())
            .andExpect(jsonPath("$.createdAt").isString())
            .andExpect(status().isCreated());
    }

    @Test
    public void testGetVehicleById() throws Exception {
        String token = getAccessToken();

        mockMvc.perform(get("/api/v1/vehicles/" + testVehicleId)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plateNumber").isString())
            .andExpect(jsonPath("$.brand").isString())
            .andExpect(jsonPath("$.model").isString())
            .andExpect(jsonPath("$.year").isNumber())
            .andExpect(jsonPath("$.mileageKM").isNumber())
            .andExpect(jsonPath("$.status").isString())
            .andExpect(jsonPath("$.commissionedAt").isString())
            .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    public void testUpdateVehicleById() throws Exception {
        String token = getAccessToken();
        VehicleUpdateRequest request = new VehicleUpdateRequest(
            null, 
            null, 
            null, 
            2025, 
            12500,
            Vehicle.VehicleCondition.EXCELLENT,
            null
        );

        mockMvc.perform(patch("/api/v1/vehicles/" + testVehicleId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.plateNumber").isString())
            .andExpect(jsonPath("$.brand").isString())
            .andExpect(jsonPath("$.model").isString())
            .andExpect(jsonPath("$.year").isNumber())
            .andExpect(jsonPath("$.mileageKM").isNumber())
            .andExpect(jsonPath("$.status").isString())
            .andExpect(jsonPath("$.commissionedAt").isString())
            .andExpect(jsonPath("$.createdAt").isString())
            .andExpect(status().isOk());
    }

    @Test
    public void testUpdateVehicleStatus() throws Exception {
        String token = getAccessToken();
        VehicleStatusRequest request = new VehicleStatusRequest(Vehicle.VehicleStatus.RESERVED);
        
        mockMvc.perform(patch("/api/v1/vehicles/" + testVehicleId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.plateNumber").isString())
            .andExpect(jsonPath("$.brand").isString())
            .andExpect(jsonPath("$.model").isString())
            .andExpect(jsonPath("$.year").isNumber())
            .andExpect(jsonPath("$.mileageKM").isNumber())
            .andExpect(jsonPath("$.status").isString())
            .andExpect(jsonPath("$.commissionedAt").isString())
            .andExpect(jsonPath("$.createdAt").isString())
            .andExpect(status().isOk());
    }

    @Test
    public void testDeleteVehicleById() throws Exception {
        String token = getAccessToken();

        mockMvc.perform(delete("/api/v1/vehicles/" + testVehicleId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

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
