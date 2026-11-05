package app.parkyn.security;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import app.parkyn.IntegrationTest;
import app.parkyn.TestRequests;
import app.parkyn.TestSessions;
import app.parkyn.TestUsers;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import jakarta.servlet.http.Cookie;

// C4 : sessions actives (une par famille = un appareil), révocation par l'utilisateur, logout de l'appareil courant
@AutoConfigureMockMvc
@IntegrationTest
public class ActiveSessionsTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String EMAIL = "alice@parkyn.app";
    private static final String OTHER_EMAIL = "bob@parkyn.app";
    private static final String TENANT_B_EMAIL = "carol@b.test";
    private static final String ADMIN_EMAIL = "admin@parkyn.app";
    private static final String PASSWORD = "Password123!";
    // Message du 404 métier : distingue la famille introuvable d'une route inexistante
    private static final String SESSION_NOT_FOUND = "Session introuvable";
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private TestUsers testUsers;
    private TestSessions testSessions;

    // Résultat d'un login : le cookie (la famille) et l'access token
    private record Device(Cookie cookie, String accessToken, String userAgent) {}

    @BeforeEach
    void setUp() {
        testUsers = new TestUsers(userRepository, jdbcTemplate, passwordEncoder);
        testSessions = new TestSessions(jdbcTemplate);
        testUsers.create(EMAIL, PASSWORD, User.Role.DRIVER);
    }

    // --- Liste ---

    @Test
    public void listShowsOneEntryPerFamilyAndMarksCurrent() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");
        Device phone = loginWith(EMAIL, "Phone");
        // Une rotation ajoute une ligne à la famille du téléphone, pas une entrée dans la liste
        Device phoneAfterRefresh = refresh(phone);

        mockMvc.perform(listSessions(laptop.accessToken(), phoneAfterRefresh.cookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                familyOf(laptop.cookie()).toString(), familyOf(phoneAfterRefresh.cookie()).toString())))
            .andExpect(jsonPath("$[*].user_agent", containsInAnyOrder("Laptop", "Phone")))
            .andExpect(jsonPath("$[?(@.user_agent == 'Phone')].current").value(true))
            .andExpect(jsonPath("$[?(@.user_agent == 'Laptop')].current").value(false))
            .andExpect(jsonPath("$[0].ip", notNullValue()));
    }

    // Connexion = première session de la famille (login), dernière activité = session active (dernier refresh)
    @Test
    public void listShowsSignInAndLastActivityOfFamily() throws Exception {
        Device phone = loginWith(EMAIL, "Phone");
        Device phoneAfterRefresh = refresh(phone);
        UUID family = familyOf(phone.cookie());
        OffsetDateTime loginAt = jdbcTemplate.queryForObject(
            "SELECT MIN(created_at) FROM user_sessions WHERE family_id = ?", OffsetDateTime.class, family);
        OffsetDateTime refreshAt = jdbcTemplate.queryForObject(
            "SELECT created_at FROM user_sessions WHERE family_id = ? AND revoked_at IS NULL", OffsetDateTime.class, family);
        assertTrue(loginAt.isBefore(refreshAt), "Le refresh doit créer une session plus récente que le login");

        String body = mockMvc.perform(listSessions(phoneAfterRefresh.accessToken(), phoneAfterRefresh.cookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andReturn().getResponse().getContentAsString();

        String signedInAt = JsonPath.read(body, "$[0].signed_in_at");
        String lastActiveAt = JsonPath.read(body, "$[0].last_active_at");
        assertEquals(loginAt.toInstant(), OffsetDateTime.parse(signedInAt).toInstant());
        assertEquals(refreshAt.toInstant(), OffsetDateTime.parse(lastActiveAt).toInstant());
    }

    @Test
    public void listWithoutCookieHasNoCurrentSession() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(listSessions(laptop.accessToken(), null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].current").value(false));
    }

    @Test
    public void listHidesRevokedAndExpiredSessions() throws Exception {
        User alice = userRepository.findByEmail(EMAIL).orElseThrow();
        testSessions.create(alice, OffsetDateTime.now().minusMinutes(1));
        Device loggedOut = loginWith(EMAIL, "Old");
        logout(loggedOut).andExpect(status().isNoContent());
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(listSessions(laptop.accessToken(), laptop.cookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].user_agent").value("Laptop"));
    }

    @Test
    public void listDoesNotShowOtherUsersSessions() throws Exception {
        createTenantBUser();
        testUsers.create(OTHER_EMAIL, PASSWORD, User.Role.DRIVER);
        loginWith(OTHER_EMAIL, "Bob");
        loginWith(TENANT_B_EMAIL, "Carol");
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(listSessions(laptop.accessToken(), laptop.cookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].user_agent").value("Laptop"));
    }

    // --- Révocation d'un appareil ---

    @Test
    public void revokeOwnFamily() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");
        Device phone = loginWith(EMAIL, "Phone");

        mockMvc.perform(revokeSession(laptop.accessToken(), familyOf(phone.cookie())))
            .andExpect(status().isNoContent());

        refreshRequest(phone.cookie()).andExpect(status().isUnauthorized());
        refreshRequest(laptop.cookie()).andExpect(status().isOk());
    }

    @Test
    public void revokeFamilyOfAnotherUserIsNotFound() throws Exception {
        testUsers.create(OTHER_EMAIL, PASSWORD, User.Role.DRIVER);
        Device bob = loginWith(OTHER_EMAIL, "Bob");
        Device laptop = loginWith(EMAIL, "Laptop");

        // Même réponse qu'un id inexistant : on ne révèle pas que la famille existe
        mockMvc.perform(revokeSession(laptop.accessToken(), familyOf(bob.cookie())))
            .andExpect(status().isNotFound())
            .andExpect(content().string(SESSION_NOT_FOUND));

        assertEquals(1, activeSessionsInFamily(bob.cookie()));
    }

    @Test
    public void revokeFamilyOfAnotherTenantIsNotFound() throws Exception {
        createTenantBUser();
        Device carol = loginWith(TENANT_B_EMAIL, "Carol");
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(revokeSession(laptop.accessToken(), familyOf(carol.cookie())))
            .andExpect(status().isNotFound())
            .andExpect(content().string(SESSION_NOT_FOUND));

        assertEquals(1, activeSessionsInFamily(carol.cookie()));
    }

    @Test
    public void revokeUnknownFamilyIsNotFound() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(revokeSession(laptop.accessToken(), UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(content().string(SESSION_NOT_FOUND));
    }

    // Famille expirée (non révoquée) : absente de la liste, donc même 404 qu'une famille inconnue
    @Test
    public void revokeExpiredFamilyIsNotFound() throws Exception {
        User alice = userRepository.findByEmail(EMAIL).orElseThrow();
        Cookie expired = new Cookie("refresh_token", testSessions.create(alice, OffsetDateTime.now().minusMinutes(1)));
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(revokeSession(laptop.accessToken(), familyOf(expired)))
            .andExpect(status().isNotFound())
            .andExpect(content().string(SESSION_NOT_FOUND));
    }

    // --- Déconnecter les autres appareils ---

    @Test
    public void revokeOtherSessionsKeepsCurrent() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");
        Device phone = loginWith(EMAIL, "Phone");
        Device tablet = loginWith(EMAIL, "Tablet");

        mockMvc.perform(revokeOtherSessions(tablet.accessToken(), tablet.cookie()))
            .andExpect(status().isNoContent());

        assertEquals(0, activeSessionsInFamily(laptop.cookie()));
        assertEquals(0, activeSessionsInFamily(phone.cookie()));
        refreshRequest(tablet.cookie()).andExpect(status().isOk());
    }

    @Test
    public void revokeOtherSessionsDoesNotTouchOtherUsers() throws Exception {
        testUsers.create(OTHER_EMAIL, PASSWORD, User.Role.DRIVER);
        Device bob = loginWith(OTHER_EMAIL, "Bob");
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(revokeOtherSessions(laptop.accessToken(), laptop.cookie()))
            .andExpect(status().isNoContent());

        assertEquals(1, activeSessionsInFamily(bob.cookie()));
    }

    // Sans cookie, l'appareil courant n'a pas de session : toutes les familles sont « autres »
    @Test
    public void revokeOtherSessionsWithoutCookieRevokesAll() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");
        Device phone = loginWith(EMAIL, "Phone");

        mockMvc.perform(revokeOtherSessions(laptop.accessToken(), null))
            .andExpect(status().isNoContent());

        assertEquals(0, activeSessionsInFamily(laptop.cookie()));
        assertEquals(0, activeSessionsInFamily(phone.cookie()));
    }

    // Cookie d'un autre utilisateur : ignoré comme une absence de cookie, ses sessions ne sont pas touchées
    @Test
    public void revokeOtherSessionsWithCookieOfAnotherUserRevokesOnlyOwnSessions() throws Exception {
        testUsers.create(OTHER_EMAIL, PASSWORD, User.Role.DRIVER);
        Device bob = loginWith(OTHER_EMAIL, "Bob");
        Device laptop = loginWith(EMAIL, "Laptop");

        mockMvc.perform(revokeOtherSessions(laptop.accessToken(), bob.cookie()))
            .andExpect(status().isNoContent());

        assertEquals(0, activeSessionsInFamily(laptop.cookie()));
        assertEquals(1, activeSessionsInFamily(bob.cookie()));
    }

    // --- Logout de l'appareil courant ---

    @Test
    public void logoutRevokesOnlyCurrentFamily() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");
        Device phone = loginWith(EMAIL, "Phone");

        logout(phone).andExpect(status().isNoContent());

        assertEquals(0, activeSessionsInFamily(phone.cookie()));
        refreshRequest(laptop.cookie()).andExpect(status().isOk());
    }

    @Test
    public void logoutIgnoresCookieOfAnotherUser() throws Exception {
        testUsers.create(OTHER_EMAIL, PASSWORD, User.Role.DRIVER);
        Device bob = loginWith(OTHER_EMAIL, "Bob");
        Device laptop = loginWith(EMAIL, "Laptop");

        logout(new Device(bob.cookie(), laptop.accessToken(), null)).andExpect(status().isNoContent());

        assertEquals(1, activeSessionsInFamily(bob.cookie()));
        assertEquals(1, activeSessionsInFamily(laptop.cookie()));
    }

    @Test
    public void logoutWithoutCookieRevokesNothing() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");

        logout(new Device(null, laptop.accessToken(), null)).andExpect(status().isNoContent());

        assertEquals(1, activeSessionsInFamily(laptop.cookie()));
    }

    // Le logout reste authentifié par l'access token : le cookie seul ne suffit pas
    @Test
    public void logoutWithCookieOnlyIsRefused() throws Exception {
        Device laptop = loginWith(EMAIL, "Laptop");

        logout(new Device(laptop.cookie(), null, null)).andExpect(status().isForbidden());

        assertEquals(1, activeSessionsInFamily(laptop.cookie()));
    }

    // --- Désactivation du compte ---

    @Test
    public void deletingUserRevokesAllItsSessions() throws Exception {
        User admin = testUsers.create(ADMIN_EMAIL, PASSWORD, User.Role.ADMIN);
        User alice = userRepository.findByEmail(EMAIL).orElseThrow();
        Device laptop = loginWith(EMAIL, "Laptop");
        Device phone = loginWith(EMAIL, "Phone");
        Device adminDevice = loginWith(admin.getEmail(), "Admin");

        mockMvc.perform(delete("/api/v1/users/" + alice.getId())
                .header("Authorization", "Bearer " + adminDevice.accessToken()))
            .andExpect(status().isNoContent());

        // Révocation immédiate en base, sans attendre un refresh
        assertEquals(0, activeSessionsInFamily(laptop.cookie()));
        assertEquals(0, activeSessionsInFamily(phone.cookie()));
        assertEquals(1, activeSessionsInFamily(adminDevice.cookie()));
    }

    @Test
    public void deactivatingUserByUpdateRevokesAllItsSessions() throws Exception {
        User admin = testUsers.create(ADMIN_EMAIL, PASSWORD, User.Role.ADMIN);
        User alice = userRepository.findByEmail(EMAIL).orElseThrow();
        Device laptop = loginWith(EMAIL, "Laptop");
        Device adminDevice = loginWith(admin.getEmail(), "Admin");

        mockMvc.perform(patch("/api/v1/users/" + alice.getId())
                .header("Authorization", "Bearer " + adminDevice.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isOk());

        assertEquals(0, activeSessionsInFamily(laptop.cookie()));
    }

    // --- Helpers ---

    private void createTenantBUser() {
        jdbcTemplate.update("INSERT INTO tenants (id, name) VALUES (?, ?)", TENANT_B, "Entreprise B");
        testUsers.inTenant(TENANT_B).create(TENANT_B_EMAIL, PASSWORD, User.Role.DRIVER);
    }

    private Device loginWith(String email, String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .header("User-Agent", userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return deviceOf(result, userAgent);
    }

    // Le navigateur renvoie son user agent à chaque refresh : la nouvelle session de la famille le garde
    private Device refresh(Device device) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                .header("User-Agent", device.userAgent())
                .with(TestRequests.csrfToken())
                .cookie(device.cookie()))
            .andExpect(status().isOk())
            .andReturn();
        return deviceOf(result, device.userAgent());
    }

    private Device deviceOf(MvcResult result, String userAgent) throws Exception {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(cookie);
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
        return new Device(cookie, accessToken, userAgent);
    }

    private ResultActions refreshRequest(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
            .with(TestRequests.csrfToken())
            .cookie(cookie));
    }

    private ResultActions logout(Device device) throws Exception {
        var request = post("/api/v1/auth/logout");
        if (device.accessToken() != null) {
            request.header("Authorization", "Bearer " + device.accessToken());
        }
        if (device.cookie() != null) {
            request.cookie(device.cookie());
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.RequestBuilder listSessions(String accessToken, Cookie cookie) {
        var request = get("/api/v1/auth/sessions").header("Authorization", "Bearer " + accessToken);
        if (cookie != null) {
            request.cookie(cookie);
        }
        return request;
    }

    private org.springframework.test.web.servlet.RequestBuilder revokeSession(String accessToken, UUID familyId) {
        return delete("/api/v1/auth/sessions/" + familyId).header("Authorization", "Bearer " + accessToken);
    }

    private org.springframework.test.web.servlet.RequestBuilder revokeOtherSessions(String accessToken, Cookie cookie) {
        var request = delete("/api/v1/auth/sessions").header("Authorization", "Bearer " + accessToken);
        if (cookie != null) {
            request.cookie(cookie);
        }
        return request;
    }

    // Requêtes SQL directes : le test lit ce qui est réellement en base
    private UUID familyOf(Cookie cookie) throws Exception {
        return jdbcTemplate.queryForObject(
            "SELECT family_id FROM user_sessions WHERE refresh_token_hash = ?",
            UUID.class, sha256Hex(cookie.getValue()));
    }

    private int activeSessionsInFamily(Cookie cookie) throws Exception {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_sessions WHERE family_id = ? AND revoked_at IS NULL",
            Integer.class, familyOf(cookie));
    }

    private static String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
