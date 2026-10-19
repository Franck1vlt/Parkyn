package app.parkyn;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.UserRequest;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@IntegrationTest
public class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired VehicleRepository vehicleRepository;

    private static final String TEST_EMAIL = "test@parkyn.app";
    private static final String TEST_EMAIL2 = "test2@parkyn.app";
    private static final String TEST_EMAIL3 = "test3@parkyn.app";
    private static final String TEST_PASSWORD = "Password123!";
    private String testRefreshToken;
    private String testRefreshToken2;
    private UUID testUserId;
    private UUID testAdminId;
    private UUID testDriverId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(TEST_EMAIL);
        user.setHashedPwd(passwordEncoder.encode(TEST_PASSWORD));
        user.setRole(User.Role.ADMIN);
        user.setActive(true);
        userRepository.saveAndFlush(user);
        testAdminId = user.getId();

        User user2 = new User();
        user2.setFirstName("Test");
        user2.setLastName("User2");
        user2.setEmail(TEST_EMAIL2);
        user2.setHashedPwd(passwordEncoder.encode(TEST_PASSWORD));
        user2.setRole(User.Role.AGENT);
        user2.setActive(true);
        userRepository.saveAndFlush(user2);

        testRefreshToken = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(testRefreshToken);
        rt.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(rt);

        testRefreshToken2 = UUID.randomUUID().toString();
        RefreshToken rt2 = new RefreshToken();
        rt2.setUser(user2);
        rt2.setToken(testRefreshToken2);
        rt2.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(rt2);

        testUserId = user.getId();

        User user3 = new User();
        user3.setFirstName("Test");
        user3.setLastName("Driver");
        user3.setEmail(TEST_EMAIL3);
        user3.setHashedPwd(passwordEncoder.encode(TEST_PASSWORD));
        user3.setRole(User.Role.DRIVER);
        user3.setActive(true);
        userRepository.saveAndFlush(user3);
        testDriverId = user3.getId();
    }

    @Test
    public void testGetUsers() throws Exception {
        String token = getAccessToken(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].firstname").isString())
            .andExpect(jsonPath("$.content[0].lastname").isString())
            .andExpect(jsonPath("$.content[0].email").isString())
            .andExpect(jsonPath("$.content[0].role").isString())
            .andExpect(jsonPath("$.content[0].active").isBoolean())
            .andExpect(jsonPath("$.content[0].createdAt").isString())
            .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    public void testGetUsersWithAgentUser() throws Exception {
        String token = getAccessToken(TEST_EMAIL2, TEST_PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testAdminAddAgent() throws Exception {
        String token = getAccessToken(TEST_EMAIL, TEST_PASSWORD);
        UserRequest request = new UserRequest(
            "John",
            "Doe",
            "john.doe@parkyn.app",
            "Password123!",
            User.Role.AGENT,
            true
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstname").isString())
            .andExpect(jsonPath("$.lastname").isString())
            .andExpect(jsonPath("$.email").isString())
            .andExpect(jsonPath("$.role").isString())
            .andExpect(jsonPath("$.active").isBoolean())
            .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    public void testAddUserWithEmailAlreadyExists() throws Exception {
        String token = getAccessToken(TEST_EMAIL, TEST_PASSWORD);
        UserRequest request = new UserRequest(
            "John",
            "Doe",
            TEST_EMAIL,
            "Password123!",
            User.Role.AGENT,
            true
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());
    }

    @Test
    public void testAgentAddDriver() throws Exception {
        String token = getAccessToken(TEST_EMAIL2, TEST_PASSWORD);
        UserRequest request = new UserRequest(
            "Jane",
            "Smith",
            "jane.smith@parkyn.app",
            "Password123!",
            User.Role.DRIVER,
            true
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstname").isString())
            .andExpect(jsonPath("$.lastname").isString())
            .andExpect(jsonPath("$.email").isString())
            .andExpect(jsonPath("$.role").isString())
            .andExpect(jsonPath("$.active").isBoolean())
            .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    public void testAgentAddAdmin() throws Exception {
        String token = getAccessToken(TEST_EMAIL2, TEST_PASSWORD);
        UserRequest request = new UserRequest(
            "Alice",
            "Johnson",
            "alice.johnson@parkyn.app",
            "Password123!",
            User.Role.ADMIN,
            true
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testAgentAddAgent() throws Exception {
        String token = getAccessToken(TEST_EMAIL2, TEST_PASSWORD);
        UserRequest request = new UserRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.AGENT,
            true
        );

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    public void testAdminUpdateUserById() throws Exception {
        String token = getAccessToken(TEST_EMAIL, TEST_PASSWORD);
        UserUpdateRequest request = new UserUpdateRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.DRIVER,
            true
        );

        mockMvc.perform(patch("/api/v1/users/" + testUserId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstname").isString())
            .andExpect(jsonPath("$.lastname").isString())
            .andExpect(jsonPath("$.email").isString())
            .andExpect(jsonPath("$.role").isString())
            .andExpect(jsonPath("$.active").isBoolean())
            .andExpect(jsonPath("$.createdAt").isString());  
    }

    @Test
    public void testAgentUpdateUserById() throws Exception {
        String token = getAccessToken(TEST_EMAIL2, TEST_PASSWORD);
        UserUpdateRequest request = new UserUpdateRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.DRIVER,
            true
        );

        mockMvc.perform(patch("/api/v1/users/" + testAdminId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden()) ;

        // F1 : un AGENT ne modifie pas un ADMIN, et rien n'est enregistré
        User admin = userRepository.findById(testAdminId).orElseThrow();
        assertEquals("User", admin.getLastName());
        assertEquals(User.Role.ADMIN, admin.getRole());
    }

    @Test
    public void testAgentUpdateDriverWithAdminRole() throws Exception {
        String token = getAccessToken(TEST_EMAIL2, TEST_PASSWORD);
        UserUpdateRequest request = new UserUpdateRequest(
            "Bob",
            "Williams",
            "bob.williams@parkyn.app",
            "Password123!",
            User.Role.ADMIN,
            true
        );

        mockMvc.perform(patch("/api/v1/users/" + testDriverId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());

        User driver = userRepository.findById(testDriverId).orElseThrow();
        assertEquals(User.Role.DRIVER, driver.getRole());
    }

    @Test
    public void testDeleteUser() throws Exception {
        String token = getAccessToken(TEST_EMAIL, TEST_PASSWORD);
        
        mockMvc.perform(delete("/api/v1/users/" + testUserId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent()); 
    }

    @Test
    public void testReactivateUser() throws Exception {
        String token = getAccessToken(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(delete("/api/v1/users/" + testUserId)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/users/" + testUserId + "/reactivate")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstname").isString())
            .andExpect(jsonPath("$.lastname").isString())
            .andExpect(jsonPath("$.email").isString())
            .andExpect(jsonPath("$.role").isString())
            .andExpect(jsonPath("$.active").isBoolean())
            .andExpect(jsonPath("$.createdAt").isString());  
    }
    
    @Test
    public void testGetDrivers() throws Exception {
        String token = getAccessToken(TEST_EMAIL2, TEST_PASSWORD);

        mockMvc.perform(get("/api/v1/users/drivers")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].firstname").isString())
            .andExpect(jsonPath("$.content[0].lastname").isString())
            .andExpect(jsonPath("$.content[0].email").isString())
            .andExpect(jsonPath("$.content[0].role").isString())
            .andExpect(jsonPath("$.content[0].active").isBoolean())
            .andExpect(jsonPath("$.content[0].createdAt").isString())
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    private String getAccessToken(String email, String password) throws Exception {
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
