package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.service.PermissionService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@AutoConfigureMockMvc 
@IntegrationTest 
public class PermissionCacheTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoSpyBean UserRepository userRepository;
    @Autowired PermissionService permissionService;


    private static final String ADMIN_EMAIL = "admin@parkyn.app";
    private static final String AGENT_EMAIL = "agent@parkyn.app";
    private static final String PASSWORD = "Password123!";
    private UUID userId;

    @BeforeEach 
    void setUp() {
        // Créer un utilisateur avec un rôle (ex: AGENT)
        User user = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(AGENT_EMAIL, PASSWORD, User.Role.AGENT);
        userRepository.save(user);
        userId = user.getId();

        // Créer un utilisateur avec un rôle ADMIN
        User admin = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(ADMIN_EMAIL, PASSWORD, User.Role.ADMIN);
        userRepository.save(admin);
    }

    @Test 
    void testPermissionCache() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                .header("Authorization", "Bearer " + login(AGENT_EMAIL, PASSWORD)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reservations")
                .header("Authorization", "Bearer " + login(AGENT_EMAIL, PASSWORD)))
            .andExpect(status().isOk());
            
        permissionService.permissionCodesOf(userId);
        permissionService.permissionCodesOf(userId);
        verify(userRepository, times(1)).findPermissionCodesByUserId(userId);
    }

    @Test 
    void testRoleDowngradeInvalidatesCache() throws Exception {
        // 1. AGENT appelle GET /reservations : 200
        mockMvc.perform(get("/api/v1/reservations")
                .header("Authorization", "Bearer " + login(AGENT_EMAIL, PASSWORD)))
            .andExpect(status().isOk());

        // 2. ADMIN rétrograde l'AGENT en DRIVER
        UserUpdateRequest userUpdateRequest = new UserUpdateRequest(null, null, null, null, User.Role.DRIVER, null);
        mockMvc.perform(patch("/api/v1/users/" + userId)
                .header("Authorization", "Bearer " + login(ADMIN_EMAIL, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userUpdateRequest)))
            .andExpect(status().isOk());
        // 3. AGENT rappelle GET /reservations : 403
        mockMvc.perform(get("/api/v1/reservations")
                .header("Authorization", "Bearer " + login(AGENT_EMAIL, PASSWORD)))
            .andExpect(status().isForbidden());
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
