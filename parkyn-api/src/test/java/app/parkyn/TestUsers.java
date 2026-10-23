package app.parkyn;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;

/**
 * Crée des utilisateurs de test valables pour les deux systèmes d'autorisation
 * pendant la cohabitation (A3) : l'ancien rôle (users.role) et le nouveau (user_roles).
 * Au Contract, retirer setRole(...).
 */
public class TestUsers {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public TestUsers(UserRepository userRepository, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
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
        userRepository.saveAndFlush(user);

        jdbc.update(
            "INSERT INTO user_roles (user_id, role_id) "
                + "SELECT ?, id FROM roles WHERE name = ? AND tenant_id IS NULL",
            user.getId(), role.name());
        return user;
    }
}
