package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.tenant.Tenants;

/**
 * Identifiant de requête (X-Request-Id) : présent sur toutes les réponses, jamais pris tel quel s'il est douteux,
 * et ligne de log ERROR pour chaque 4xx/5xx avec requestId, tenantId et userId dans le MDC.
 */
@AutoConfigureMockMvc
@IntegrationTest
public class RequestIdTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private static final String HEADER = "X-Request-Id";
    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    // Le filtre logge sous son propre nom de classe
    private static final String FILTER_LOGGER = "app.parkyn.security.RequestIdFilter";
    private static final String PASSWORD = "password123!";

    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(FILTER_LOGGER);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    // --- En-tête présent sur toutes les réponses ---

    @Test
    void successResponseHasRequestId() throws Exception {
        User admin = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword("admin@parkyn.app", User.Role.ADMIN);

        requestId(mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(admin)))
            .andExpect(status().isOk()));
    }

    @Test
    void validationErrorHasRequestId() throws Exception {
        requestId(mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstname\":\"\",\"lastname\":\"\",\"email\":\"x\",\"password\":\"x\"}"))
            .andExpect(status().isBadRequest()));
    }

    @Test
    void unauthorizedResponseHasRequestId() throws Exception {
        requestId(mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()));
    }

    // 401 posé directement par JwtAuthFilter, sans passer par le point d'entrée
    @Test
    void invalidTokenResponseHasRequestId() throws Exception {
        requestId(mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
            .andExpect(status().isUnauthorized()));
    }

    // 429 posé par RateLimitFilter, avant la lecture du corps
    @Test
    void rateLimitedResponseHasRequestId() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.20").andExpect(status().isUnauthorized());
        }

        requestId(login("127.0.0.20").andExpect(status().isTooManyRequests()));
    }

    // --- Valeur reçue ---

    // Identifiant de nginx ($request_id : 32 hexadécimaux) ou UUID : repris tel quel
    @ParameterizedTest(name = "{0} repris tel quel")
    @ValueSource(strings = {"0b8f9f0e-6c1e-4d52-9a55-3d6a1d2b7c10", "9f86d081884c7d659a2feaa0c55ad015"})
    void validClientValueIsKept(String sent) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").header(HEADER, sent))
            .andExpect(status().isUnauthorized()).andReturn();

        assertEquals(sent, result.getResponse().getHeader(HEADER));
    }

    // Valeur trop longue, trop courte, avec retour à la ligne (injection de logs), espace, HTML, vide : un UUID à la place
    @ParameterizedTest(name = "valeur douteuse n° {index} remplacée par un UUID")
    @ValueSource(strings = {
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "abc",
        "abcdefgh\r\nX-Evil: 1",
        "abcdefgh\nINFO fake log line",
        "has space inside",
        "<script>alert(1)</script>",
        ""})
    void unsafeClientValueIsReplaced(String sent) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").header(HEADER, sent))
            .andExpect(status().isUnauthorized()).andReturn();

        String id = result.getResponse().getHeader(HEADER);
        assertNotNull(id, "En-tête X-Request-Id absent");
        assertTrue(id.matches(UUID_PATTERN), "UUID attendu, reçu : " + id);
        assertNotEquals(sent, id);
        assertFalse(id.contains("\r") || id.contains("\n"));
    }

    @Test
    void twoRequestsHaveDifferentIds() throws Exception {
        String first = requestId(mockMvc.perform(get("/api/v1/auth/me")));
        String second = requestId(mockMvc.perform(get("/api/v1/auth/me")));

        assertNotEquals(first, second);
    }

    // --- Logs ---

    // Un refus est une erreur : ligne ERROR avec méthode, chemin et statut ; jamais la query string (un token pourrait s'y trouver)
    @Test
    void failedRequestIsLoggedAsErrorWithoutQueryString() throws Exception {
        String id = requestId(mockMvc.perform(get("/api/v1/auth/me?token=secret-value"))
            .andExpect(status().isUnauthorized()));

        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains("GET") && message.contains("/api/v1/auth/me") && message.contains("401"), message);
        assertFalse(message.contains("secret-value") || message.contains("token="), message);
        assertEquals(id, event.getMDCPropertyMap().get("requestId"));
        assertNull(event.getMDCPropertyMap().get("tenantId"));
        assertNull(event.getMDCPropertyMap().get("userId"));
    }

    // 403 d'un utilisateur connecté : tenant et utilisateur dans le MDC, jamais son email
    @Test
    void authenticatedErrorLogCarriesTenantAndUserButNoEmail() throws Exception {
        String email = "driver@parkyn.app";
        User driver = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword(email, User.Role.DRIVER);

        String id = requestId(mockMvc.perform(get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(driver)))
            .andExpect(status().isForbidden()));

        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals(id, event.getMDCPropertyMap().get("requestId"));
        assertEquals(Tenants.DEFAULT_ID.toString(), event.getMDCPropertyMap().get("tenantId"));
        assertEquals(driver.getId().toString(), event.getMDCPropertyMap().get("userId"));
        assertFalse(event.getFormattedMessage().contains(email));
        assertFalse(event.getMDCPropertyMap().values().stream().anyMatch(value -> value.contains(email)));
    }

    @Test
    void successfulRequestIsNotLoggedAsError() throws Exception {
        User admin = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword("admin@parkyn.app", User.Role.ADMIN);

        requestId(mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(admin)))
            .andExpect(status().isOk()));

        assertTrue(logs.list.isEmpty());
    }

    // Le MDC est lié au thread : s'il n'est pas vidé, la requête suivante hérite de l'identité de la précédente
    @Test
    void mdcIsClearedAfterTheRequest() throws Exception {
        User driver = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword("driver@parkyn.app", User.Role.DRIVER);

        requestId(mockMvc.perform(get("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(driver)))
            .andExpect(status().isForbidden()));

        for (String key : List.of("requestId", "tenantId", "userId")) {
            assertNull(MDC.get(key), key + " resté dans le MDC");
        }
    }

    // --- Aides ---

    // Vérifie la présence d'un UUID dans l'en-tête de réponse et le renvoie
    private String requestId(ResultActions result) throws Exception {
        String id = result.andReturn().getResponse().getHeader(HEADER);
        assertNotNull(id, "En-tête X-Request-Id absent");
        assertTrue(id.matches(UUID_PATTERN), "UUID attendu, reçu : " + id);
        return id;
    }

    private ResultActions login(String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
            .with(request -> {
                request.setRemoteAddr(ip);
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"unknown@example.com\",\"password\":\"" + PASSWORD + "\"}"));
    }
}
