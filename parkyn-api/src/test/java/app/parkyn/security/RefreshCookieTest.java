package app.parkyn.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

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
    // Même message pour tous les refus : le client ne doit pas savoir si le token a existé
    private static final String SESSION_EXPIRED = "Session expirée, veuillez vous reconnecter";

    @BeforeEach
    void setUp() {
        new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(EMAIL, PASSWORD, User.Role.DRIVER);
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                ;
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
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(SESSION_EXPIRED));
    }

    @Test 
    public void refreshWithUnknownCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
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
            .cookie(refreshCookie))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
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
                .cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(SESSION_EXPIRED));
    }

}
