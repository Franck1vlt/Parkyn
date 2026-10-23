package app.parkyn;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.tenant.Tenants;

/**
 * Crée des utilisateurs de test : le compte, son rôle système (ligne user_roles) et son tenant.
 * Par défaut, les comptes sont créés dans le tenant par défaut ; inTenant(...) en change.
 */
public class TestUsers {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final UUID tenantId;

    public TestUsers(UserRepository userRepository, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this(userRepository, jdbc, passwordEncoder, Tenants.DEFAULT_ID);
    }

    private TestUsers(UserRepository userRepository, JdbcTemplate jdbc, PasswordEncoder passwordEncoder, UUID tenantId) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tenantId = tenantId;
    }

    // Mêmes méthodes, pour les comptes d'un autre tenant (le tenant doit exister dans la table tenants)
    public TestUsers inTenant(UUID otherTenantId) {
        return new TestUsers(userRepository, jdbc, passwordEncoder, otherTenantId);
    }

    // Utilisateur capable de se connecter via /auth/login (vrai hash du mot de passe)
    public User create(String email, String password, User.Role role) {
        return save(email, passwordEncoder.encode(password), role);
    }

    // Utilisateur qui ne se connecte jamais (token généré directement) : pas de hash, plus rapide
    public User createWithoutPassword(String email, User.Role role) {
        return save(email, "not-used", role);
    }

    // Utilisateur dont le hash est fourni tel quel (ex. ancien hash BCrypt sans préfixe, avant Argon2id)
    public User createWithHash(String email, String hashedPassword, User.Role role) {
        return save(email, hashedPassword, role);
    }

    private User save(String email, String hashedPassword, User.Role role) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName(role.name());
        user.setHashedPwd(hashedPassword);
        user.setActive(true);
        // Créé hors connexion (contexte racine) : le tenant doit être fixé, comme à l'inscription
        user.setTenantId(tenantId);
        userRepository.saveAndFlush(user);

        jdbc.update(
            "INSERT INTO user_roles (user_id, role_id) "
                + "SELECT ?, id FROM roles WHERE name = ? AND tenant_id IS NULL",
            user.getId(), role.name());
        return user;
    }
}
