package app.parkyn.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.model.User;
import app.parkyn.model.VerificationToken;
import app.parkyn.repository.VerificationTokenRepository;

@Service 
public class VerificationTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits, 43 caractères en Base64 URL

    private final SecureRandom random = new SecureRandom();
    private final VerificationTokenRepository repository;
    private final Duration ttl;

    public VerificationTokenService(
            VerificationTokenRepository repository,
            @Value("${parkyn.verification.token-ttl}") Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Verification token TTL must be positive");
        }
        this.repository = repository;
        this.ttl = ttl;
    }

    // Crée le jeton du compte et renvoie sa valeur en clair, à n'utiliser que pour le lien de l'email
    public String issue(User user) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        random.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setTokenHash(sha256Hex(token));
        verificationToken.setExpiresAt(OffsetDateTime.now().plus(ttl));
        repository.save(verificationToken);
        return token;
    }

    // SHA-256 en hexadécimal (64 caractères) : déterministe, pour retrouver le jeton reçu. 
    // Pas d'Argon2id : le jeton a 256 bits d'aléa, un hash lent n'apporte rien et empêcherait la recherche
    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // Vérifie et consomme le jeton s'il est valide
    @Transactional 
    public void verify(String token) {
        VerificationToken verificationToken = repository.findByTokenHash(sha256Hex(token))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid verification token"));

        if (verificationToken.getConsumedAt() != null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid verification token");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!verificationToken.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                HttpStatus.GONE, "Verification token expired");
        }

        User user = verificationToken.getUser();

        if (user.getStatus() != User.Status.PENDING_EMAIL_VERIFICATION) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid verification token");
        }

        user.setStatus(User.Status.ACTIVE);
        verificationToken.setConsumedAt(now);
    }
}
