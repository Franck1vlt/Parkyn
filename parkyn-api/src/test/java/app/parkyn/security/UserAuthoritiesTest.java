package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;

@IntegrationTest 
public class UserAuthoritiesTest {

    @Autowired UserDetailsService userDetailsService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtAuthFilter jwtAuthFilter;
    @Autowired JwtService jwtService;

    @ParameterizedTest(name = "{0} : {1} autorités")
    @MethodSource("rolesAndAuthorities")
    void roleHasExpectedAuthorities(User.Role role, int expectedAuthorityCount,
            String[] expectedPermissions, String[] forbiddenPermissions)
            throws ServletException, IOException {
        String email = "test." + role.name().toLowerCase() + "@parkyn.app";

        new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword(email, role);

        // Vérifie l'authentification effectivement construite par JwtAuthFilter.
        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(email));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.clearContext();
        jwtAuthFilter.doFilter(request, response, (req, res) -> { });

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication != null, "Authentification absente après le passage dans JwtAuthFilter");

        Set<String> authorities = authentication.getAuthorities().stream()
            .map(auth -> auth.getAuthority())
            .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_" + role.name()), "ROLE_" + role.name() + " manquant");
        for (String permission : expectedPermissions) {
            assertTrue(authorities.contains(permission), permission + " attendue pour " + role);
        }
        for (String permission : forbiddenPermissions) {
            assertFalse(authorities.contains(permission), permission + " interdite pour " + role);
        }
        assertEquals(expectedAuthorityCount, authorities.size());
        SecurityContextHolder.clearContext();
    }

    // Nombre d'autorités = 1 ROLE_ + permissions du rôle (V6 + V11)
    private static Stream<Arguments> rolesAndAuthorities() {
        return Stream.of(
            Arguments.of(User.Role.ADMIN, 19,
                new String[] { "vehicle:delete", "user:create", "roles:manage" },
                new String[] {}),
            Arguments.of(User.Role.AGENT, 16,
                new String[] { "user:create", "user:update", "user:status:update", "vehicle:create" },
                new String[] { "vehicle:delete", "roles:manage", "user:roles:assign" }),
            Arguments.of(User.Role.DRIVER, 8,
                new String[] { "reservation:read:own", "reservation:create:own", "user:read:own" },
                new String[] { "reservation:read:all", "user:create", "vehicle:delete", "dashboard:read:all" })
        );
    }

}
