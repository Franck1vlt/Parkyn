package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import app.parkyn.TestUsers;
import app.parkyn.TestcontainersConfig;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;

@SpringBootTest (webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles ("test")
@Import (TestcontainersConfig.class)
@Sql (scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ErrorPageSecurityTest {

    @LocalServerPort int port;   // le port réel choisi au démarrage

    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String adminToken;
    private final String EMAIL = "admin@parkyn.app";
    private final String PASSWORD = "Password123!";

    @BeforeEach 
    void setUp() {
        User admin = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .create(EMAIL, PASSWORD, User.Role.ADMIN);
        userRepository.saveAndFlush(admin);
        adminToken = jwtService.generateToken(admin);
    }

    // Une route inexistante sous /api/v1 passe par /error, qui est refusé par la sécurité
    @Test 
    void notFoundStaysNotFoundThroughErrorPage() throws Exception {
        // appel HTTP réel d'une route inexistante sous /api/v1 -> 403 via /error
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/route-inexistante"))
            .header("Authorization", "Bearer " + adminToken)
            .GET()
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }
}
