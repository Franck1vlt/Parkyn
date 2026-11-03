package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

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
import app.parkyn.TestUsers;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc 
@IntegrationTest 
public class CsrfTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String EMAIL = "test@parkyn.app";
    private static final String PASSWORD = "Password123!";

    @BeforeEach 
    void setUp() {
        new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(EMAIL, PASSWORD, User.Role.DRIVER);
    }

    // --- Tests ---

    // Vérifie les attributs de sécurité du cookie CSRF
    @Test 
    public void loginSetsCsrfCookie() throws Exception {
        login()
            .andExpect(cookie().exists(CSRF_COOKIE))
            .andExpect(cookie().httpOnly(CSRF_COOKIE, false))
            .andExpect(cookie().path(CSRF_COOKIE, "/"))
            .andExpect(cookie().secure(CSRF_COOKIE, true))
            .andExpect(cookie().sameSite(CSRF_COOKIE, "Strict"));
    }

    // Vérifie qu'une tentative de rafraîchissement sans jeton CSRF est refusée
    @Test 
    public void refreshWithoutCsrfTokenIsRejected() throws Exception {
        MvcResult result = login().andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(refreshCookie))
                .andExpect(status().isForbidden());
    }

    // Vérifie qu'une tentative de rafraîchissement avec un jeton CSRF incorrect est refusée
    @Test 
    public void refreshWithWrongCsrfTokenIsRejected() throws Exception {
        MvcResult result = login().andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);

        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, "wrong-token")
                .cookie(refreshCookie, csrfCookie))
            .andExpect(status().isForbidden());
    }

    // Vérifie qu'un rafraîchissement avec un jeton CSRF correspondant au cookie est accepté
    @Test 
    public void refreshWithCsrfTokenSucceeds() throws Exception {
        MvcResult result = login().andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);

        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, csrfCookie.getValue())
                .cookie(refreshCookie, csrfCookie))
            .andExpect(status().isOk());
    }

    // Vérifie qu'un rafraîchissement refusé ne fait pas tourner le jeton de rafraîchissement
    @Test 
    public void rejectedRefreshDoesNotRotateToken() throws Exception {
        MvcResult result = login().andReturn();

        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);

        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, csrfCookie.getValue())
                .cookie(refreshCookie, csrfCookie))
            .andExpect(status().isOk());
    }

    // Vérifie que les endpoints utilisant un bearer token ne nécessitent pas de jeton CSRF
    @Test 
    public void bearerEndpointsDoNotRequireCsrf() throws Exception {
        MvcResult loginResult = login().andReturn();

        String accessToken = JsonPath.read(
            loginResult.getResponse().getContentAsString(), "$.access_token"
        );

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());
    }

    // Vérifie que la connexion refuse une requête dont le corps n'est pas au format JSON
    @Test 
    public void loginRejectsNonJsonBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.TEXT_PLAIN)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isUnsupportedMediaType());
    }

    // --- Helpers ---
    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk());
    }


}
