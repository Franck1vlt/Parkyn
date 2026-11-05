package app.parkyn.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Refresh tokens : 256 bits aléatoires, stockés en base sous forme d'empreinte SHA-256.
// Un hachage lent (Argon2) est inutile ici : le token ne se devine pas, contrairement à un mot de passe.
public final class SessionTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SessionTokens() {
    }

    // 43 caractères base64url, sans padding : sûrs dans un cookie
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 64 caractères hexadécimaux (colonne refresh_token_hash)
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par toute JVM
            throw new IllegalStateException(e);
        }
    }
}
