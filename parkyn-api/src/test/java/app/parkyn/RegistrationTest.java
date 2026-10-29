package app.parkyn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.tenant.Tenants;

@AutoConfigureMockMvc 
@IntegrationTest 
public class RegistrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String FIRSTNAME = "John";
    private static final String LASTNAME = "Doe";
    private static final String ACCEPTED_EMAIL = "accepted@parkyn.app";
    private static final String PENDING_EMAIL = "pending@parkyn.app";
    private static final String CANNOT_LOGIN_EMAIL = "cannot-login@parkyn.app";
    private static final String DRIVER_EMAIL = "driver@parkyn.app";
    private static final String PASSWORD = "password123!";

    private String registerBody(String email) {
        return "{\"firstname\":\"" + FIRSTNAME + "\", \"lastname\":\"" + LASTNAME
                + "\", \"email\":\"" + email + "\", \"password\":\"" + PASSWORD + "\"}";
    }

    // Test d'une inscription réussie
    @Test 
    public void registerReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));
    }

    // Test d'une inscription avec un email non vérifié
    @Test 
    public void newAccountIsPending() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(PENDING_EMAIL)));

        User user = userRepository.findByEmail(PENDING_EMAIL).orElseThrow();
        assertEquals(User.Status.PENDING_EMAIL_VERIFICATION, user.getStatus());
    }

    // 
    @Test 
    public void newAccountCannotLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(CANNOT_LOGIN_EMAIL)));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"" + CANNOT_LOGIN_EMAIL + "\", \"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // 
    @Test 
    public void newAccountIsDriverInDefaultTenant() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(DRIVER_EMAIL)));

        User user = userRepository.findByEmail(DRIVER_EMAIL).orElseThrow();
        assertEquals(User.Role.DRIVER, user.getRole());
        assertEquals(Tenants.DEFAULT_ID, user.getTenantId());
    }

}
