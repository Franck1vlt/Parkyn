package app.parkyn;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import app.parkyn.model.User;

/**
 * Crée des sessions (user_sessions) directement en base, sans passer par /auth/login :
 * pas de limite de débit consommée, et une date d'expiration au choix (session déjà expirée comprise).
 * Renvoie le refresh token brut, à envoyer dans le cookie ; seule son empreinte SHA-256 est stockée.
 */
public class TestSessions {

    private final JdbcTemplate jdbc;

    public TestSessions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Session active, valable un jour
    public String create(User user) {
        return create(user, OffsetDateTime.now().plusDays(1));
    }

    public String create(User user, OffsetDateTime expiresAt) {
        String rawToken = UUID.randomUUID().toString();
        // created_at reculé si besoin : la contrainte chk_user_sessions_dates impose expires_at > created_at
        jdbc.update(
            "INSERT INTO user_sessions (tenant_id, user_id, refresh_token_hash, family_id, expires_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, LEAST(NOW(), ? - INTERVAL '1 day'))",
            user.getTenantId(), user.getId(), sha256Hex(rawToken), UUID.randomUUID(), expiresAt, expiresAt);
        return rawToken;
    }

    // Calcul indépendant du code de production (SessionTokens) : une erreur d'encodage côté serveur reste visible
    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
