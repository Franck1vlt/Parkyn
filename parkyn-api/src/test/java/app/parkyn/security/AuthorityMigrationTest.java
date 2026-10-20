package app.parkyn.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import app.parkyn.model.User;

import tools.jackson.databind.ObjectMapper;

import app.parkyn.IntegrationTest;
import app.parkyn.TestRequests;
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

    /**
    Ce test crée un utilisateur possédant l'ancien rôle AGENT, mais aucune permission explicite.
    Les endpoints de ReservationController ayant été migrés de hasRole() vers hasAuthority(),
    cet utilisateur ne doit plus y accéder. Le test vérifie donc que chaque requête est rejetée
    avec une réponse 403 Forbidden.
    */
    @BeforeEach
    void setUp() {
        User agent = new User();
        agent.setFirstName("Legacy");
        agent.setLastName("Agent");
        agent.setEmail("legacy.agent@example.com");
        agent.setHashedPwd("hash");
        agent.setRole(User.Role.AGENT);
        agent.setActive(true);
        userRepository.saveAndFlush(agent);
        legacyAgentToken = jwtService.generateToken(agent);
    }

    static Stream<Arguments> migratedEndpoints() {
        // les 4 endpoints de ReservationController (méthode, URL, corps valide)
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/api/v1/reservations", null),
            Arguments.of(HttpMethod.POST, "/api/v1/reservations", TestRequests.validReservationRequest()),
            Arguments.of(HttpMethod.GET, "/api/v1/reservations/" + UUID.randomUUID(), null),
            Arguments.of(HttpMethod.POST, "/api/v1/reservations/" + UUID.randomUUID() + "/return", TestRequests.validReturnVehicleRequest()),
            Arguments.of(HttpMethod.GET, "/api/v1/dashboard", null)
        );
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("migratedEndpoints")
    void legacyRoleWithoutPermissionIsDenied(HttpMethod method, String path, Object body) throws Exception {
        // appeler l'endpoint avec legacyAgentToken, attendre 403
        MockHttpServletRequestBuilder requestBuilder = request(method, path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + legacyAgentToken);

        if (body != null) {
            requestBuilder.contentType("application/json")
                .content(objectMapper.writeValueAsString(body));
        }

        mockMvc.perform(requestBuilder)
            .andExpect(status().isForbidden());
    }
}
