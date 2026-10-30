package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.dto.RegisterRequest;
import app.parkyn.email.EmailRequest;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc 
@IntegrationTest 
@RecordApplicationEvents
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RateLimitTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationEvents applicationEvents;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "password123!";

    // --- Tests ----

    // Test qui vérifie que la limite des connexions est appliquée par adresse IP
    @Test 
    public void loginIsLimitedPerIp() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.2", null)
                    .andExpect(status().isUnauthorized());
        }

        login("127.0.0.2", null)
                .andExpect(status().isTooManyRequests());
    }

    // Test qui vérifie que l’en-tête Retry-After est présent quand la limite est atteinte
    @Test 
    public void limitedLoginHasRetryAfter() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.3", null)
                .andExpect(status().isUnauthorized());
        }

        MvcResult result = login("127.0.0.3", null)
            .andExpect(status().isTooManyRequests())
            .andReturn();

        String retryAfter = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertNotNull(retryAfter);
        assertTrue(Long.parseLong(retryAfter) > 0);
    }

    // Test qui vérifie qu’un calcul Argon2id n’est pas effectué pour une requête refusée, afin d’éviter la saturation du serveur
    @Test 
    public void limitedLoginDoesNotHashPassword() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.4", null)
                .andExpect(status().isUnauthorized());
        }

        verify(passwordEncoder, atLeastOnce()).matches(any(CharSequence.class), anyString());
        clearInvocations(passwordEncoder);

        login("127.0.0.4", null)
            .andExpect(status().isTooManyRequests());

        verify(passwordEncoder, never()).matches(any(CharSequence.class), anyString());
    }

    // Test qui vérifie que la limite est appliquée par adresse IP, pas par client
    @Test 
    public void otherIpIsNotLimited() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.6", null)
                .andExpect(status().isUnauthorized());
        }

        login("127.0.0.6", null)
            .andExpect(status().isTooManyRequests());

        login("127.0.0.7", null)
            .andExpect(status().isUnauthorized());
    }

    // Test qui vérifie que la limite est appliquée par endpoint
    @Test 
    public void limitIsPerEndpoint() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.8", null)
                .andExpect(status().isUnauthorized());
        }

        login("127.0.0.8", null)
            .andExpect(status().isTooManyRequests());

        register("127.0.0.8", "unknown@example.com")
            .andExpect(status().isAccepted());
    }

    // Test qui vérifie que la limite des inscriptions est appliquée par adresse IP
    @Test 
    public void registerIsLimitedPerIp() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            register("127.0.0.9", "rate" + attempt + "@example.com")
                    .andExpect(status().isAccepted());
        }

        register("127.0.0.9", "unknown@example.com")
                .andExpect(status().isTooManyRequests());
    }

    // Test qui vérifie que la limite des renvois est appliquée par adresse IP
    @Test 
    public void resendIsLimitedPerIp() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            resend("127.0.0.10", "unknown@example.com")
                    .andExpect(status().isAccepted());
        }

        resend("127.0.0.10", "unknown@example.com")
                .andExpect(status().isTooManyRequests());
    }

    // Test qui vérifie que la limite des emails de vérification est appliquée par adresse email
    @Test 
    public void emailsPerAddressAreCapped() throws Exception {
        String email = "unknown@example.com";

        register("127.0.0.11", email)
                .andExpect(status().isAccepted());

        for (int attempt = 0; attempt < 4; attempt++) {
            resend("127.0.0.11", email)
                    .andExpect(status().isAccepted());
        }

        long verificationEmails = applicationEvents.stream(EmailRequest.class)
                .filter(request -> "verify-email".equals(request.template()) && email.equals(request.to()))
                .count();
        assertEquals(3, verificationEmails);
    }

    // Test qui vérifie la limite des emails "account-exists"
    @Test 
    public void emailCapAppliesToAccountExists() throws Exception {
        String email = "active@example.com";
        new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
                .create(email, PASSWORD, User.Role.DRIVER);

        for (int attempt = 0; attempt < 4; attempt++) {
            register("127.0.0.12", email)
                    .andExpect(status().isAccepted());
        }

        long accountExistsEmails = applicationEvents.stream(EmailRequest.class)
                .filter(request -> "account-exists".equals(request.template()) && email.equals(request.to()))
                .count();
        assertEquals(3, accountExistsEmails);
    }

    // Tests qui vérifient que les limites sont réinitialisées entre les tests, pour éviter les interférences
    @Test
    @Order(1)
    public void limitsAreResetBetweenTests() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.1", null)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @Order(2)
    public void limitsAreResetBetweenTestsAgain() throws Exception {
        login("127.0.0.1", null)
                .andExpect(status().isUnauthorized());
    }

    // Test qui vérifie que l’en-tête X-Real-IP d’un proxy approuvé est utilisé
    @Test 
    public void realIpHeaderFromTrustedProxyIsUsed() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
                login("172.18.0.2", "203.0.113.5")
                    .andExpect(status().isUnauthorized());
        }

        login("172.18.0.2", "203.0.113.5")
            .andExpect(status().isTooManyRequests());

        login("172.18.0.2", "203.0.113.6")
            .andExpect(status().isUnauthorized());
    }

    // Test qui vérifie que l’en-tête X-Real-IP d’un client non approuvé est ignoré
    @Test 
    public void realIpHeaderFromUntrustedClientIsIgnored() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
                login("198.51.100.7", "203.0.113." + attempt)
                    .andExpect(status().isUnauthorized());
        }

            login("198.51.100.7", "203.0.113.10")
                .andExpect(status().isTooManyRequests());
    }

    // Test qui vérifie que l’absence d’en-tête utilise l’adresse distante
    @Test 
    public void missingHeaderFallsBackToRemoteAddr() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            login("127.0.0.5", null)
                    .andExpect(status().isUnauthorized());
        }

        login("127.0.0.5", null)
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String ip, String realIpHeader) throws Exception {
        var request = post("/api/v1/auth/login")
                .with(r -> {r.setRemoteAddr(ip); return r;})
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\",\"password\":\"invalid\"}");
        if (realIpHeader != null) {
            request.header("X-Real-IP", realIpHeader);
        }
        return mockMvc.perform(request);
    }

    private ResultActions register(String ip, String email) throws Exception {
        var request = post("/api/v1/auth/register")
                .with(r -> {r.setRemoteAddr(ip); return r;})
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new RegisterRequest("Test", "User", email, PASSWORD)));
        return mockMvc.perform(request);
    }

    private ResultActions resend(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/resend-verification")
                .with(r -> {r.setRemoteAddr(ip); return r;})
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

}
