package app.parkyn.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import app.parkyn.repository.UserRepository;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.IntegrationTest;
import app.parkyn.TestRequests;
import app.parkyn.TestUsers;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
@IntegrationTest
public class RefreshCookieTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Value("${jwt.refresh-expiration-days}") long refreshDays;

    private static final String EMAIL = "test@parkyn.app";
    private static final String PASSWORD = "Password123!";
    private static final String SESSION_EXPIRED = "Session expirée, veuillez vous reconnecter";
    private static final Integer THREADS = 10;

    @BeforeEach
    void setUp() {
        new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(EMAIL, PASSWORD, User.Role.DRIVER);
    }

    @Test 
    public void loginSetsRefreshCookie() throws Exception {
        login()
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(cookie().httpOnly("refresh_token", true))
            .andExpect(cookie().secure("refresh_token", true))
            .andExpect(cookie().sameSite("refresh_token", "Strict"))
            .andExpect(cookie().path("refresh_token", "/api/v1/auth/refresh"))
            .andExpect(cookie().maxAge("refresh_token", (int) Duration.ofDays(refreshDays).toSeconds()));
    }

    @Test 
    public void loginBodyHasNoRefreshToken() throws Exception {
        login()
            .andExpect(jsonPath("$.refresh_token").doesNotExist())
            .andExpect(jsonPath("$.access_token").exists());
    }

    @Test 
    public void refreshWithCookieReturnsNewAccessToken() throws Exception {
        MvcResult result = login().andReturn();

        var refreshCookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken())
                .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(cookie().httpOnly("refresh_token", true))
            .andExpect(cookie().path("refresh_token", "/api/v1/auth/refresh"))
            .andExpect(cookie().maxAge("refresh_token", (int) Duration.ofDays(refreshDays).toSeconds()))
            .andReturn();

        var rotatedRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertNotNull(rotatedRefreshCookie);
        assertNotEquals(refreshCookie.getValue(), rotatedRefreshCookie.getValue());
    }

    @Test
    public void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(SESSION_EXPIRED));
    }

    @Test 
    public void refreshWithUnknownCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken())
                .cookie(new Cookie("refresh_token", "inconnu")))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(SESSION_EXPIRED));
    }

    @Test
    public void refreshTokenInBodyIsIgnored() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(OffsetDateTime.now().plusSeconds(86400));
        refreshTokenRepository.save(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"" + refreshToken.getToken() + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(SESSION_EXPIRED));
    }

    @Test 
    public void oldRefreshCookieIsRejectedAfterRotation() throws Exception {
        MvcResult loginResult = login().andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken())
                .cookie(refreshCookie))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken())
                .cookie(refreshCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(SESSION_EXPIRED));
    }

    @Test
    public void expiredRefreshCookieIsRejected() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken())
                .cookie(new Cookie("refresh_token", refreshToken.getToken())))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(SESSION_EXPIRED));
    }

    @Test 
    public void logoutClearsRefreshCookie() throws Exception {
        MvcResult loginResult = login().andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);
        String accessToken = JsonPath.read(
            loginResult.getResponse().getContentAsString(), "$.access_token");

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent())
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(cookie().value("refresh_token", ""))
            .andExpect(cookie().maxAge("refresh_token", 0))
            .andExpect(cookie().path("refresh_token", "/api/v1/auth/refresh"))
            .andExpect(cookie().httpOnly("refresh_token", true))
            .andExpect(cookie().secure("refresh_token", true))
            .andExpect(cookie().sameSite("refresh_token", "Strict"));
    }

    @Test 
    public void logoutRevokesRefreshToken() throws Exception {
        MvcResult loginResult = login().andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);
        String accessToken = JsonPath.read(
            loginResult.getResponse().getContentAsString(), "$.access_token");

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(TestRequests.csrfToken())
                .cookie(refreshCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(SESSION_EXPIRED));
    }

    @Test
    public void concurrentRefreshCookiesOnlyOneSucceeds() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();

        // Token créé en base à chaque manche : pas de login, donc pas de limite de débit consommée
        for (int round = 0; round < 10; round++) {
            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setUser(user);
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setExpiresAt(OffsetDateTime.now().plusDays(1));
            refreshTokenRepository.save(refreshToken);
            Cookie refreshCookie = new Cookie("refresh_token", refreshToken.getToken());

            Integer activeBefore = activeRefreshTokens(user);

            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            List<Future<Integer>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS), "Départ jamais donné");
                        return mockMvc.perform(post("/api/v1/auth/refresh")
                                .with(TestRequests.csrfToken())
                                .cookie(refreshCookie))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    }));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS), "Threads jamais prêts");
                start.countDown();

                // Timeouts : un blocage (pool saturé, verrou) fait échouer le test au lieu de figer la suite
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> future : futures) {
                    statuses.add(future.get(10, TimeUnit.SECONDS));
                }

                long ok = statuses.stream().filter(status -> status == 200).count();
                assertEquals(1, ok, () -> "Statuts : " + statuses);
                assertEquals(THREADS - 1,
                    statuses.stream().filter(status -> status == 401).count());

                Integer activeAfter = activeRefreshTokens(user);
                assertEquals(activeBefore, activeAfter,
                    () -> "Tokens actifs avant/après : " + activeBefore + "/" + activeAfter);
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    // --- Helpers ---
    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk());
    }

    private Integer activeRefreshTokens(User user) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens "
                + "WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            user.getId());
    }
}
