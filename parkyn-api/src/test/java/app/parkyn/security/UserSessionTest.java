package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import app.parkyn.IntegrationTest;
import app.parkyn.TestRequests;
import app.parkyn.TestUsers;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
@IntegrationTest
public class UserSessionTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String EMAIL = "test@parkyn.app";
    private static final String PASSWORD = "Password123!";
    private static final String USER_AGENT = "User-Test-Agent";
    // nginx (proxy de confiance) transmet l'IP du visiteur dans X-Real-IP
    private static final String PROXY_IP = "127.0.0.5";
    private static final String CLIENT_IP = "203.0.113.10";
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private TestUsers testUsers;

    @BeforeEach
    void setUp() {
        testUsers = new TestUsers(userRepository, jdbcTemplate, passwordEncoder);
        testUsers.create(EMAIL, PASSWORD, User.Role.DRIVER);
    }

    // --- Tests ---

    @Test
    public void loginStoresHashedToken() throws Exception {
        Cookie refreshCookie = refreshCookieOf(login(EMAIL));

        assertEquals(1, sessionCount(EMAIL));
        String storedHash = jdbcTemplate.queryForObject(
            "SELECT refresh_token_hash FROM user_sessions WHERE user_id = ?",
            String.class, userId(EMAIL));

        // Le token n'est jamais stocké tel quel, seulement son empreinte SHA-256
        assertNotEquals(refreshCookie.getValue(), storedHash);
        assertEquals(sha256Hex(refreshCookie.getValue()), storedHash);
    }

    // En-tête choisi par le client, plus long que la colonne (255) : tronqué, pas de 500 au login
    @Test
    public void longUserAgentIsTruncated() throws Exception {
        String longUserAgent = "A".repeat(300);
        loginFrom(longUserAgent, PROXY_IP, CLIENT_IP);

        String stored = jdbcTemplate.queryForObject(
            "SELECT user_agent FROM user_sessions WHERE user_id = ?", String.class, userId(EMAIL));
        assertEquals(longUserAgent.substring(0, 255), stored);
    }

    @Test
    public void loginStoresUserAgentAndIp() throws Exception {
        loginFrom(USER_AGENT, PROXY_IP, CLIENT_IP);

        assertEquals(1, sessionCount(EMAIL));
        Map<String, Object> session = jdbcTemplate.queryForMap(
            "SELECT user_agent, ip FROM user_sessions WHERE user_id = ?", userId(EMAIL));

        assertEquals(USER_AGENT, session.get("user_agent"));
        // IP lue par ClientIpResolver (X-Real-IP), pas l'adresse du proxy
        assertEquals(CLIENT_IP, session.get("ip"));
    }

    @Test
    public void sessionBelongsToUserTenant() throws Exception {
        // Utilisateur hors du tenant par défaut : un code qui écrirait toujours Tenants.DEFAULT_ID échouerait
        jdbcTemplate.update("INSERT INTO tenants (id, name) VALUES (?, ?)", TENANT_B, "Entreprise B");
        String emailB = "driver@b.test";
        testUsers.inTenant(TENANT_B).create(emailB, PASSWORD, User.Role.DRIVER);

        login(emailB);

        assertEquals(1, sessionCount(emailB));
        UUID sessionTenant = jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM user_sessions WHERE user_id = ?", UUID.class, userId(emailB));

        assertEquals(TENANT_B, sessionTenant);
    }

    @Test
    public void refreshKeepsFamily() throws Exception {
        Cookie oldCookie = refreshCookieOf(login(EMAIL));
        Cookie newCookie = refreshCookieOf(refresh(oldCookie).andExpect(status().isOk()));

        assertEquals(2, sessionCount(EMAIL));
        assertEquals(1, revokedCount(oldCookie));
        assertEquals(familyOf(oldCookie), familyOf(newCookie));
        assertEquals(1, activeSessionsInFamily(newCookie));
    }

    @Test
    public void reuseRevokesWholeFamily() throws Exception {
        Cookie oldCookie = refreshCookieOf(login(EMAIL));
        Cookie newCookie = refreshCookieOf(refresh(oldCookie).andExpect(status().isOk()));

        // Révocation ancienne : on sort de la période de grâce
        ageRevocation(oldCookie, 3600);

        refresh(oldCookie).andExpect(status().isUnauthorized());

        assertEquals(0, activeSessionsInFamily(newCookie));
        refresh(newCookie).andExpect(status().isUnauthorized());
    }

    @Test
    public void reuseWithinGraceDoesNotRevokeFamily() throws Exception {
        Cookie oldCookie = refreshCookieOf(login(EMAIL));
        Cookie newCookie = refreshCookieOf(refresh(oldCookie).andExpect(status().isOk()));

        // Juste après la rotation (deux onglets en même temps) : refus simple, la famille reste active
        refresh(oldCookie).andExpect(status().isUnauthorized());

        assertEquals(1, activeSessionsInFamily(newCookie));
        refresh(newCookie).andExpect(status().isOk());
    }

    @Test
    public void reuseDoesNotTouchOtherFamilies() throws Exception {
        Cookie familyA = refreshCookieOf(login(EMAIL));
        Cookie familyB = refreshCookieOf(login(EMAIL));
        assertNotEquals(familyOf(familyA), familyOf(familyB));

        refreshCookieOf(refresh(familyA).andExpect(status().isOk()));
        ageRevocation(familyA, 3600);
        refresh(familyA).andExpect(status().isUnauthorized());

        assertEquals(1, activeSessionsInFamily(familyB));
        refresh(familyB).andExpect(status().isOk());
    }

    // revokeFamily n'est pas filtré par tenant (refresh en contexte ROOT) : l'isolation repose sur
    // l'unicité de family_id. Garde-fou de bout en bout ; le tenant écrit est vérifié par sessionBelongsToUserTenant
    @Test
    public void reuseDoesNotTouchOtherTenant() throws Exception {
        jdbcTemplate.update("INSERT INTO tenants (id, name) VALUES (?, ?)", TENANT_B, "Entreprise B");
        String emailB = "driver@b.test";
        testUsers.inTenant(TENANT_B).create(emailB, PASSWORD, User.Role.DRIVER);

        Cookie cookieA = refreshCookieOf(login(EMAIL));
        Cookie cookieB = refreshCookieOf(login(emailB));

        refreshCookieOf(refresh(cookieA).andExpect(status().isOk()));
        ageRevocation(cookieA, 3600);
        refresh(cookieA).andExpect(status().isUnauthorized());

        assertEquals(1, activeSessionsInFamily(cookieB));
        refresh(cookieB).andExpect(status().isOk());
    }

    @Test
    public void expiredSessionIsRevoked() throws Exception {
        Cookie refreshCookie = refreshCookieOf(login(EMAIL));

        // created_at reculé aussi : la contrainte chk_user_sessions_dates impose expires_at > created_at
        int updated = jdbcTemplate.update(
            "UPDATE user_sessions SET created_at = NOW() - INTERVAL '2 hours', "
                + "expires_at = NOW() - INTERVAL '1 hour' WHERE refresh_token_hash = ?",
            sha256Hex(refreshCookie.getValue()));
        assertEquals(1, updated, "Session introuvable pour ce token");

        refresh(refreshCookie).andExpect(status().isUnauthorized());

        assertEquals(1, revokedCount(refreshCookie));
    }

    @Test
    public void disabledUserSessionIsRevoked() throws Exception {
        Cookie refreshCookie = refreshCookieOf(login(EMAIL));
        assertEquals(1, sessionCount(EMAIL));

        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE email = ?", EMAIL);

        refresh(refreshCookie).andExpect(status().isUnauthorized());

        assertEquals(1, revokedCount(refreshCookie));
    }

    // --- Helpers ---

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(loginRequest(email)).andExpect(status().isOk());
    }

    private ResultActions loginFrom(String userAgent, String proxyIp, String clientIp) throws Exception {
        return mockMvc.perform(loginRequest(EMAIL)
                .header("User-Agent", userAgent)
                .header("X-Real-IP", clientIp)
                .with(request -> {
                    request.setRemoteAddr(proxyIp);
                    return request;
                }))
            .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder loginRequest(String email) {
        return post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
    }

    private ResultActions refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
            .with(TestRequests.csrfToken())
            .cookie(refreshCookie));
    }

    private Cookie refreshCookieOf(ResultActions result) {
        Cookie cookie = result.andReturn().getResponse().getCookie("refresh_token");
        assertNotNull(cookie);
        return cookie;
    }

    // Requêtes SQL directes : le test lit ce qui est réellement en base, sans passer par l'entité
    private UUID userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private int sessionCount(String email) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_sessions WHERE user_id = ?", Integer.class, userId(email));
    }

    private int revokedCount(Cookie refreshCookie) throws Exception {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_sessions WHERE refresh_token_hash = ? AND revoked_at IS NOT NULL",
            Integer.class, sha256Hex(refreshCookie.getValue()));
    }

    private UUID familyOf(Cookie refreshCookie) throws Exception {
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_sessions WHERE refresh_token_hash = ?",
            Integer.class, sha256Hex(refreshCookie.getValue())), "Session introuvable pour ce token");
        return jdbcTemplate.queryForObject(
            "SELECT family_id FROM user_sessions WHERE refresh_token_hash = ?",
            UUID.class, sha256Hex(refreshCookie.getValue()));
    }

    private int activeSessionsInFamily(Cookie refreshCookie) throws Exception {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_sessions WHERE family_id = ? AND revoked_at IS NULL",
            Integer.class, familyOf(refreshCookie));
    }

    private void ageRevocation(Cookie refreshCookie, int secondsAgo) throws Exception {
        int updated = jdbcTemplate.update(
            "UPDATE user_sessions SET revoked_at = NOW() - (? * INTERVAL '1 second') "
                + "WHERE refresh_token_hash = ? AND revoked_at IS NOT NULL",
            secondsAgo, sha256Hex(refreshCookie.getValue()));
        assertEquals(1, updated, "Session révoquée introuvable pour ce token");
    }

    // Calcul indépendant du code de production : le test vérifie le contrat (SHA-256 en hexadécimal)
    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
