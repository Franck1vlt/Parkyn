package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final String ADMIN_EMAIL = "test.admin@parkyn.app";

    @Test
    void adminHasRoleAndPermissions() {
        new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword(ADMIN_EMAIL, User.Role.ADMIN);

        UserDetails loadedAdmin = userDetailsService.loadUserByUsername(ADMIN_EMAIL);

        Set<String> authorities = loadedAdmin.getAuthorities().stream()
            .map(auth -> auth.getAuthority())
            .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("vehicle:delete"));
        assertTrue(authorities.contains("user:create"));
        assertEquals(19, authorities.size());
    }

}
