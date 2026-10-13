package app.parkyn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import tools.jackson.databind.ObjectMapper;

import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.RefreshRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.UserRepository;

@AutoConfigureMockMvc
@IntegrationTest
public class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private static final String TEST_EMAIL = "test@parkyn.app";
    private static final String TEST_PASSWORD = "Password123!";
    private String testRefreshToken;

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

        testRefreshToken = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(testRefreshToken);
        rt.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(rt);
    }

    @Test
    public void testLogin() throws Exception {
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isString())
            .andExpect(jsonPath("$.refresh_token").isString())
            .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    public void testRefreshToken() throws Exception {
        RefreshRequest request = new RefreshRequest(testRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isString())
            .andExpect(jsonPath("$.refresh_token").isString())
            .andExpect(jsonPath("$.token_type").value("Bearer"));
    }
    
    @Test
    public void testMe() throws Exception {
        String token = getAccessToken();
        
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(TEST_EMAIL))
            .andExpect(jsonPath("$.firstname").isString())
            .andExpect(jsonPath("$.lastname").isString())
            .andExpect(jsonPath("$.role").isString());
    } 

    @Test
    public void testLogout() throws Exception {
        String token = getAccessToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + token))
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
