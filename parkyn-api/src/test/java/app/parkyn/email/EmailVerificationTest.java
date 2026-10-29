package app.parkyn.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.RegisterRequest;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

@RecordApplicationEvents 
@AutoConfigureMockMvc 
@IntegrationTest 
public class EmailVerificationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationEvents applicationEvents;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    public static final String EMAIL = "test@example.com";
    public static final String PASSWORD = "password123!";

    // Test de l'enregistrement d'un utilisateur et de l'envoi d'un email de vérification
    @Test 
    public void registrationCreatesOneToken() throws Exception {
        String email = "registration-token-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", email, PASSWORD))))
            .andExpect(status().isAccepted());

        Integer tokenCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM verification_tokens vt
            JOIN users u ON u.id = vt.user_id
            WHERE u.email = ? AND vt.consumed_at IS NULL
            """,
            Integer.class,
            email);

        assertEquals(1, tokenCount);
    }

    // Test d'un token de vérification qui expire après 48 heures
    @Test 
    public void tokenExpiresAfter48Hours() throws Exception {
        Instant before = Instant.now();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        Instant after = Instant.now();

        Integer tokenCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM verification_tokens vt
            JOIN users u ON u.id = vt.user_id
            WHERE u.email = ? AND vt.consumed_at IS NULL
            """,
            Integer.class,
            EMAIL);

        assertEquals(1, tokenCount, "Un token de vérification doit être créé");
        
        Timestamp expiresAt = jdbcTemplate.queryForObject(
            """
            SELECT vt.expires_at
            FROM verification_tokens vt
            JOIN users u ON u.id = vt.user_id
            WHERE u.email = ? AND vt.consumed_at IS NULL
            """,
            Timestamp.class,
            EMAIL);

        Instant expiration = expiresAt.toInstant();
        Instant earliest = before.plus(Duration.ofHours(48));
        Instant latest = after.plus(Duration.ofHours(48));
        assertFalse(expiration.isBefore(earliest), "expires_at " + expiration + " avant " + earliest);
        assertFalse(expiration.isAfter(latest), "expires_at " + expiration + " après " + latest);
    }

    // Test de l'envoi d'un email de vérification après l'enregistrement
    @Test 
    public void registrationSendsVerificationEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String link = verificationLink(EMAIL);
        assertTrue(link.contains("token="), "Le lien de vérification doit contenir un token");
    }

    // Test qui vérifie qu'un seul hash du token est stocké dans la base
    @Test 
    public void onlyTheHashIsStored() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String token = tokenFrom(verificationLink(EMAIL));
        String tokenHash = jdbcTemplate.queryForObject(
            """
            SELECT vt.token_hash
            FROM verification_tokens vt
            JOIN users u ON u.id = vt.user_id
            WHERE u.email = ? AND vt.consumed_at IS NULL
            """,
            String.class,
            EMAIL);

        assertNotEquals(token, tokenHash, "Le token et son hash doivent être différents");
        assertEquals(sha256Hex(token), tokenHash);
    }

    // Test qui vérifie que les tokens sont uniques et suffisamment longs
    @Test
    public void tokensAreUniqueAndLongEnough() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Second", "User", "test2@example.com", PASSWORD))))
            .andExpect(status().isAccepted());

        String token1 = tokenFrom(verificationLink(EMAIL));
        String token2 = tokenFrom(verificationLink("test2@example.com"));

        assertNotEquals(token1, token2, "Les tokens doivent être uniques");
        assertTrue(token1.length() >= 43, "Le token doit être suffisamment long");
        assertTrue(token2.length() >= 43, "Le token doit être suffisamment long");
    }

    // Test qui vérifie qu'aucun token n'est créé pour un email déjà existant
    @Test 
    public void duplicateEmailCreatesNoToken() throws Exception {
        User existing = new TestUsers(userRepository, jdbcTemplate, passwordEncoder).create(EMAIL, PASSWORD, User.Role.DRIVER);
        assertTrue(existing.isEnabled(), "Le compte existant doit être actif avant la seconde inscription");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        Integer tokenCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM verification_tokens WHERE user_id = ?
            """,
            Integer.class,
            existing.getId()
        );

        assertEquals(0, tokenCount, "Aucun token ne doit être créé pour un email dupliqué");
        assertTrue(userRepository.findById(existing.getId()).orElseThrow().isEnabled(),
            "Le compte existant doit rester actif après la seconde inscription");
    }

    // Test qui vérifie qu'un token valide active le compte de l'utilisateur
    @Test 
    public void validTokenActivatesAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String token = tokenFrom(verificationLink(EMAIL));
        verify(token).andExpect(status().isOk());

        String accountStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE email = ?",
            String.class,
            EMAIL);
        assertEquals("ACTIVE", accountStatus, "Le statut du compte doit être ACTIVE après la vérification");
    }

    // Test qui vérifie qu'un token consommé ne peut pas être réutilisé
    @Test 
    public void validTokenIsConsumed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String token = tokenFrom(verificationLink(EMAIL));
        verify(token).andExpect(status().isOk());

        Object consumedAt = jdbcTemplate.queryForObject(
            """
            SELECT vt.consumed_at FROM verification_tokens vt
            JOIN users u ON u.id = vt.user_id
            WHERE u.email = ?
            """,
            Object.class,
            EMAIL);

        assertNotNull(consumedAt, "consumed_at doit être renseigné après la vérification");
    }

    // Test qui vérifie qu'un compte vérifié peut se connecter
    @Test 
    public void verifiedAccountCanLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String token = tokenFrom(verificationLink(EMAIL));
        verify(token).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest())))
            .andExpect(status().isOk());
    }

    // Test qui vérifie qu'un token ne peut pas être utilisé deux fois
    @Test 
    public void tokenCannotBeUsedTwice() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String token = tokenFrom(verificationLink(EMAIL));
        verify(token).andExpect(status().isOk());

        verify(token).andExpect(status().isBadRequest());
    }

    // Test qui vérifie qu'un token inconnu est rejeté
    @Test 
    public void unknownTokenIsRejected() throws Exception {
        verify("x".repeat(43)).andExpect(status().isBadRequest());
    }

    // Test qui vérifie qu'un token expiré est rejeté
    @Test 
    public void expiredTokenIsGone() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String token = tokenFrom(verificationLink(EMAIL));

        // Expire le token en modifiant la date d'expiration dans la base
        jdbcTemplate.update(
            """
            UPDATE verification_tokens SET expires_at = now() - interval '1 minute', created_at = now() - interval '2 days'
            WHERE user_id = (SELECT id FROM users WHERE email = ?)
            """,
            EMAIL);

        verify(token).andExpect(status().isGone());

        String accountStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE email = ?",
            String.class,
            EMAIL);
        assertEquals("PENDING_EMAIL_VERIFICATION", accountStatus,
            "Un token expiré ne doit pas réactiver le compte");
    }

    // Test qui vérifie qu'un token vide ou trop long est rejeté
    @Test 
    public void blankOrOversizedTokenIsRejected() throws Exception {
        verify("").andExpect(status().isBadRequest());
        verify("a".repeat(65)).andExpect(status().isBadRequest());
    }

    // Test qui vérifie qu'un compte désactivé ne peut pas être réactivé par un token
    @Test 
    public void disabledAccountIsNotReactivated() throws Exception {
        // Crée un compte
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());
        
        // Désactive le compte
        jdbcTemplate.update(
            "UPDATE users SET status = 'DISABLED', is_active = false WHERE email = ?",
            EMAIL);

        // Vérifie que le token de vérification ne réactive pas le compte
        String token = tokenFrom(verificationLink(EMAIL));
        verify(token).andExpect(status().isBadRequest());

        assertEquals("DISABLED", jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE email = ?", String.class, EMAIL));
    }

    // Test qui vérifie que les réponses pour un token inconnu et un token consommé sont identiques
    @Test 
    public void unknownAndConsumedGiveSameResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Test", "User", EMAIL, PASSWORD))))
            .andExpect(status().isAccepted());

        String token = tokenFrom(verificationLink(EMAIL));
        verify(token).andExpect(status().isOk());

        var unknown = verify("x".repeat(43)).andReturn().getResponse();
        var consumed = verify(token).andReturn().getResponse();
        assertEquals(400, unknown.getStatus());
        assertEquals(unknown.getStatus(), consumed.getStatus());
        assertEquals(unknown.getContentAsString(), consumed.getContentAsString());
    }     

    // --- Helpers ---

    // Lien de l'email de vérification envoyé à cette adresse
    private String verificationLink(String email) {
        return applicationEvents.stream(EmailRequest.class)
            .filter(request -> "verify-email".equals(request.template()) && email.equals(request.to()))
            .map(request -> (String) request.variables().get("link"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Aucun email de vérification pour " + email));
    }

    private static String tokenFrom(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    // SHA-256 en hexadécimal (64 caractères) : déterministe, pour retrouver le jeton reçu. 
    // Pas d'Argon2id : le jeton a 256 bits d'aléa, un hash lent n'apporte rien et empêcherait la recherche
    private static String sha256Hex(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private LoginRequest validLoginRequest() {
        return new LoginRequest(EMAIL, PASSWORD);
    }

}
