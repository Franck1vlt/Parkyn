package app.parkyn.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;

@AutoConfigureMockMvc 
@IntegrationTest 
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String adminToken;
    private final String EMAIL = "admin@parkyn.app";
    private final String PASSWORD = "Password123!";

    @BeforeEach 
    void setUp() {
        User admin = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(EMAIL, PASSWORD, User.Role.ADMIN);
        userRepository.saveAndFlush(admin);
        adminToken = jwtService.generateToken(admin);
    }

    @Test 
    void urlOutsideApiIsDeniedEvenWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/internal/test")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}

