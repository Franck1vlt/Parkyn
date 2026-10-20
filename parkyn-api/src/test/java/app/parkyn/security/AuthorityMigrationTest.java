package app.parkyn.security;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
// import app.parkyn.AuthorizationMatrixTest.MatrixCase;
import app.parkyn.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;

import app.parkyn.IntegrationTest;
import app.parkyn.repository.UserRepository;

/**
 * Test temporaire de la bascule A3 (hasRole -> hasAuthority).
 * Un utilisateur avec l'ancien rôle mais SANS permission doit être refusé
 * sur chaque endpoint basculé. À supprimer au Contract.
 */
@AutoConfigureMockMvc 
@IntegrationTest 
public class AuthorityMigrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    private String legacyAgentToken;

    @BeforeEach
    void setUp() {
        User agent = new User();
        agent.setRole(User.Role.AGENT);
        agent.setFirstName("Legacy");
        agent.setLastName("Agent");
        agent.setEmail("legacy.agent@example.com");
        userRepository.saveAndFlush(agent);  
        legacyAgentToken = jwtService.generateToken(agent);
    }

    // static Stream<MatrixCase> migratedEndpoints() {
    //     // les 4 endpoints de ReservationController (méthode, URL, corps valide)
    // }

    // @ParameterizedTest(name = "{0}")
    // @MethodSource("migratedEndpoints")
    // void legacyRoleWithoutPermissionIsDenied(...) throws Exception {
    //     // appeler l'endpoint avec legacyAgentToken, attendre 403
    // }
}
