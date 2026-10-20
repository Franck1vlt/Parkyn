package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @ParameterizedTest(name = "{0} : {1} autorités")
    @MethodSource("rolesAndAuthorities")
    void roleHasExpectedAuthorities(User.Role role, int expectedAuthorityCount,
            String[] expectedPermissions, String[] forbiddenPermissions) {
        String email = "test." + role.name().toLowerCase() + "@parkyn.app";

        new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword(email, role);

        // Rechargé comme le fait JwtAuthFilter : rôles et permissions viennent de la base
        UserDetails loadedUser = userDetailsService.loadUserByUsername(email);

        Set<String> authorities = loadedUser.getAuthorities().stream()
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
