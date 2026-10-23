package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import app.parkyn.IntegrationTest;
import app.parkyn.TestRequests;
import app.parkyn.TestUsers;
import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.RegisterRequest;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * A4 : hachage Argon2id. Les nouveaux hash sont en Argon2id ({argon2}), les anciens hash BCrypt
 * (sans préfixe) restent valides et sont convertis en Argon2id à la prochaine connexion réussie.
 */
@AutoConfigureMockMvc
@IntegrationTest
class PasswordHashingTest {

    private static final String PASSWORD = "Password123!";
    private static final String ARGON2_PREFIX = "{argon2}";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    // Inscription : le hash créé est en Argon2id
    @Test
    void registeredAccountIsHashedWithArgon2id() throws Exception {
        RegisterRequest request = TestRequests.validRegisterRequest();
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        String hash = hashOf(User.normalizeEmail(request.email()));
        assertTrue(hash.startsWith(ARGON2_PREFIX), "Hash attendu en Argon2id, obtenu : " + hash);
        assertTrue(passwordEncoder.matches(request.password(), hash), "Le hash doit correspondre au mot de passe");
    }

    // Ancien hash BCrypt sans préfixe : la connexion réussit et le hash est converti en Argon2id
    @Test
    void legacyBcryptHashIsUpgradedOnSuccessfulLogin() throws Exception {
        String email = "legacy@parkyn.app";
        String legacyHash = new BCryptPasswordEncoder().encode(PASSWORD);
        users().createWithHash(email, legacyHash, User.Role.AGENT);

        login(email, PASSWORD).andExpect(status().isOk());

        String hash = hashOf(email);
        assertTrue(hash.startsWith(ARGON2_PREFIX), "Hash attendu en Argon2id après connexion, obtenu : " + hash);
        assertTrue(passwordEncoder.matches(PASSWORD, hash), "Le nouveau hash doit correspondre au mot de passe");
    }

    // Après conversion, une seconde connexion fonctionne avec le nouveau hash
    @Test
    void upgradedAccountCanLogInAgain() throws Exception {
        String email = "legacy2@parkyn.app";
        users().createWithHash(email, new BCryptPasswordEncoder().encode(PASSWORD), User.Role.DRIVER);

        login(email, PASSWORD).andExpect(status().isOk());
        String upgradedHash = hashOf(email);
        login(email, PASSWORD).andExpect(status().isOk());

        assertTrue(upgradedHash.startsWith(ARGON2_PREFIX), "Hash attendu en Argon2id, obtenu : " + upgradedHash);
        assertEquals(upgradedHash, hashOf(email), "Un hash déjà en Argon2id ne doit pas être recalculé");
    }

    // Mauvais mot de passe sur un ancien hash : refus, et le hash n'est jamais modifié
    @Test
    void failedLoginNeverChangesLegacyHash() throws Exception {
        String email = "legacy3@parkyn.app";
        String legacyHash = new BCryptPasswordEncoder().encode(PASSWORD);
        users().createWithHash(email, legacyHash, User.Role.AGENT);

        login(email, "MauvaisMotDePasse!").andExpect(status().isUnauthorized());

        assertEquals(legacyHash, hashOf(email));
    }

    // Compte déjà en Argon2id : la connexion ne recalcule pas le hash
    @Test
    void argon2idHashIsNotRecomputedOnLogin() throws Exception {
        String email = "argon@parkyn.app";
        users().create(email, PASSWORD, User.Role.AGENT);
        String hashBefore = hashOf(email);

        login(email, PASSWORD).andExpect(status().isOk());

        assertTrue(hashBefore.startsWith(ARGON2_PREFIX), "Hash attendu en Argon2id, obtenu : " + hashBefore);
        assertEquals(hashBefore, hashOf(email));
    }

    // --- Aides ---

    private TestUsers users() {
        return new TestUsers(userRepository, jdbcTemplate, passwordEncoder);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    // Lu directement en base : c'est la valeur réellement stockée qui compte
    private String hashOf(String email) {
        return jdbcTemplate.queryForObject("SELECT hashed_pwd FROM users WHERE email = ?", String.class, email);
    }
}
