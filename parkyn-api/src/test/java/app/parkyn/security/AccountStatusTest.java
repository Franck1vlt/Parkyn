package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Map;
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
import app.parkyn.dto.RefreshRequest;
import app.parkyn.dto.UserRequest;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc 
@IntegrationTest 
public class AccountStatusTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    private TestUsers testUsers;

    private static final String LOGIN_EMAIL = "admin@parkyn.app";
    private static final String LOGIN_PASSWORD = "Password123!";

    @BeforeEach 
    void setUp() {
        testUsers = new TestUsers(userRepository, jdbcTemplate, passwordEncoder);
    }


    // Login : le status "PENDING_EMAIL_VERIFICATION" empêche la connexion
    @Test 
    void pendingAccountCannotLogIn() throws Exception {
        User user = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        statusUpdate("PENDING_EMAIL_VERIFICATION", user.getEmail());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest())))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string("Email ou mot de passe incorrect"));
    }

    // Login : le status "DISABLED" empêche la connexion
    @Test
    void disabledAccountCannotLogIn() throws Exception {
        User user = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        statusUpdate("DISABLED", user.getEmail());

        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginRequest())))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string("Email ou mot de passe incorrect"));
    }


    // Login : le status "DISABLED" empêche la connexion, mais la réponse est identique à celle d'un mot de passe incorrect
    @Test 
    void statusAnswerIsIndistinguishable() throws Exception {
        User user = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        statusUpdate("DISABLED", user.getEmail());

        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginRequest())))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string("Email ou mot de passe incorrect"));

        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginRequest(LOGIN_EMAIL, "WrongPassword"))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string("Email ou mot de passe incorrect"));

        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginRequest("unknown@parkyn.app", LOGIN_PASSWORD))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string("Email ou mot de passe incorrect"));
    }

    // Un token JWT d'un compte désactivé est refusé par /auth/me
    @Test 
    void tokenOfDisabledAccountIsRefused() throws Exception {
        User user = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        statusUpdate("DISABLED", user.getEmail());

        mockMvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());

        // Le refus vient du filtre : un endpoint métier est fermé lui aussi
        mockMvc.perform(get("/api/v1/users")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    // Un compte en attente de vérification n'est pas actif : son token est refusé aussi
    @Test
    void tokenOfPendingAccountIsRefused() throws Exception {
        User user = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        String token = jwtService.generateToken(user);

        statusUpdate("PENDING_EMAIL_VERIFICATION", user.getEmail());

        mockMvc.perform(get("/api/v1/users")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    // Le refresh token d'un compte désactivé ne donne plus de nouvel access token
    @Test
    void refreshOfDisabledAccountIsRefused() throws Exception {
        User user = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(OffsetDateTime.now().plusDays(1));
        refreshTokenRepository.saveAndFlush(refreshToken);

        disable(user.getEmail());

        mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken.getToken()))))
            .andExpect(status().isUnauthorized());
    }

    // Un conducteur en attente de vérification n'apparaît pas dans la liste des conducteurs
    @Test
    void pendingDriverIsNotListed() throws Exception {
        User admin = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        testUsers.create("active@parkyn.app", "Password123!", User.Role.DRIVER);
        User pending = testUsers.create("pending@parkyn.app", "Password123!", User.Role.DRIVER);
        pending(pending.getEmail());

        mockMvc.perform(get("/api/v1/users/drivers")
            .header("Authorization", "Bearer " + jwtService.generateToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].email", containsInAnyOrder("active@parkyn.app")));
    }

    // Sauvegarder un compte en attente (ex. : changer le prénom) ne le rend pas actif
    @Test
    void savingPendingAccountKeepsItPending() {
        User user = testUsers.create("pending@parkyn.app", "Password123!", User.Role.DRIVER);
        pending(user.getEmail());

        User loaded = userRepository.findById(user.getId()).orElseThrow();
        loaded.setFirstName("Renamed");
        userRepository.saveAndFlush(loaded);

        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT status, is_active FROM users WHERE id = ?", user.getId());
        assertEquals("PENDING_EMAIL_VERIFICATION", account.get("status"));
        assertEquals(false, account.get("is_active"));
    }

    // La réactivation ne sert qu'aux comptes désactivés : elle ne saute pas la vérification d'email
    @Test
    void reactivationDoesNotActivatePendingAccount() throws Exception {
        User admin = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        User user = testUsers.create("pending@parkyn.app", "Password123!", User.Role.DRIVER);
        pending(user.getEmail());

        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/reactivate")
            .header("Authorization", "Bearer " + jwtService.generateToken(admin)))
            .andExpect(status().isNotFound());

        assertEquals("PENDING_EMAIL_VERIFICATION", jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE id = ?", String.class, user.getId()));
    }

    // Un ADMIN fait DELETE /users/{id} sur un conducteur
    // Attendu : En base : status = 'DISABLED' et is_active = false
    @Test 
    void deactivationSetsDisabledStatus() throws Exception {
        User admin = testUsers
            .create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        User user = testUsers
            .create("driver@parkyn.app", "Password123!", User.Role.DRIVER);

        mockMvc.perform(delete("/api/v1/users/" + user.getId())
            .header("Authorization", "Bearer " + jwtService.generateToken(admin)))
            .andExpect(status().isNoContent());

        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT status, is_active FROM users WHERE id = ?", user.getId());
        assertEquals("DISABLED", account.get("status"));
        assertEquals(false, account.get("is_active"));
    }

    // Puis PATCH /users/{id}/reactivate	
    // Attendu : En base : status = 'ACTIVE' et is_active = true
    @Test
    void reactivationSetsActiveStatus() throws Exception {
        User admin = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        User user = testUsers.create("driver@parkyn.app", "Password123!", User.Role.DRIVER);
        
        disable(user.getEmail());

        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/reactivate")
            .header("Authorization", "Bearer " + jwtService.generateToken(admin)))
            .andExpect(status().isOk());

        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT status, is_active FROM users WHERE id = ?", user.getId());
        assertEquals("ACTIVE", account.get("status"));
        assertEquals(true, account.get("is_active"));
    }

    // POST /users par un ADMIN
    // Attendu : En base : status = 'ACTIVE'. Pour l'instant, B2 changera l'inscription
    @Test 
    void createdAccountsAreActive() throws Exception {
        User admin = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);

        mockMvc.perform(post("/api/v1/users")
            .header("Authorization", "Bearer " + jwtService.generateToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UserRequest("Driver", "Test", "driver@parkyn.app", "Password123!", User.Role.DRIVER, true))))
            .andExpect(status().isCreated());

        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT status, is_active FROM users WHERE email = ?", "driver@parkyn.app");
        assertEquals("ACTIVE", account.get("status"));
        assertEquals(true, account.get("is_active"));
    }

    @Test
    void createdInactiveAccountsAreDisabled() throws Exception {
        User admin = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);

        mockMvc.perform(post("/api/v1/users")
            .header("Authorization", "Bearer " + jwtService.generateToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UserRequest("Driver", "Test", "driver@parkyn.app", "Password123!", User.Role.DRIVER, false))))
            .andExpect(status().isCreated());

        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT status, is_active FROM users WHERE email = ?", "driver@parkyn.app");
        assertEquals("DISABLED", account.get("status"));
        assertEquals(false, account.get("is_active"));
    }

    // PATCH /users/{id} avec active = false : même effet qu'une désactivation
    @Test
    void updateWithActiveFalseSetsDisabledStatus() throws Exception {
        User admin = testUsers.create(LOGIN_EMAIL, LOGIN_PASSWORD, User.Role.ADMIN);
        User user = testUsers.create("driver@parkyn.app", "Password123!", User.Role.DRIVER);

        mockMvc.perform(patch("/api/v1/users/" + user.getId())
            .header("Authorization", "Bearer " + jwtService.generateToken(admin))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UserUpdateRequest(null, null, null, null, null, false))))
            .andExpect(status().isOk());

        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT status, is_active FROM users WHERE id = ?", user.getId());
        assertEquals("DISABLED", account.get("status"));
        assertEquals(false, account.get("is_active"));
    }


    // --- Aides ---

    private LoginRequest validLoginRequest() {
        return new LoginRequest(LOGIN_EMAIL, LOGIN_PASSWORD);
    }

    private void statusUpdate(String status, String email) {
        jdbcTemplate.update(
            "UPDATE users SET status = ? WHERE email = ?",
            status,
            email);
    }

    // Compte désactivé tel que l'application le produit : les deux colonnes
    private void disable(String email) {
        jdbcTemplate.update(
            "UPDATE users SET status = 'DISABLED', is_active = false WHERE email = ?",
            email);
    }

    // Compte en attente de vérification tel que l'application le produira : is_active = (status == ACTIVE)
    private void pending(String email) {
        jdbcTemplate.update(
            "UPDATE users SET status = 'PENDING_EMAIL_VERIFICATION', is_active = false WHERE email = ?",
            email);
    }


}
