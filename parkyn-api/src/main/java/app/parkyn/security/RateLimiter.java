package app.parkyn.security;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

@Component
public class RateLimiter {

    public static final String LOGIN = "login";
    public static final String REGISTER = "register";
    public static final String RESEND = "resend";
    public static final String EMAIL = "email";

    // Borné en taille et en durée : un attaquant qui change d'IP ne peut pas remplir la mémoire
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    // Règle → fabrique du seau (un seau par règle et par valeur : IP ou adresse email)
    private final Map<String, Supplier<Bucket>> rules;

    public RateLimiter(
            @Value("${parkyn.rate-limit.login-per-minute}") int loginPerMinute,
            @Value("${parkyn.rate-limit.login-per-hour}") int loginPerHour,
            @Value("${parkyn.rate-limit.register-per-hour}") int registerPerHour,
            @Value("${parkyn.rate-limit.resend-per-hour}") int resendPerHour,
            @Value("${parkyn.rate-limit.emails-per-address-per-hour}") int emailsPerAddressPerHour) {
        rules = Map.of(
            // Deux limites : une rafale attend 1 minute, une attaque prolongée jusqu'à 1 heure
            LOGIN, () -> Bucket.builder()
                    .addLimit(limit -> limit.capacity(loginPerMinute).refillGreedy(loginPerMinute, Duration.ofMinutes(1)))
                    .addLimit(limit -> limit.capacity(loginPerHour).refillGreedy(loginPerHour, Duration.ofHours(1)))
                    .build(),
            REGISTER, () -> perHour(registerPerHour),
            RESEND, () -> perHour(resendPerHour),
            EMAIL, () -> perHour(emailsPerAddressPerHour));
    }

    public ConsumptionProbe tryConsume(String rule, String value) {
        Supplier<Bucket> factory = rules.get(rule);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown rate limit rule: " + rule);
        }
        return buckets.get(rule + ":" + value, key -> factory.get()).tryConsumeAndReturnRemaining(1);
    }

    // Plafond silencieux des emails d'inscription par adresse : false = ne pas envoyer
    public boolean tryConsumeEmail(String address) {
        return tryConsume(EMAIL, address).isConsumed();
    }

    // Délai avant le prochain jeton, arrondi à la seconde supérieure (en-tête Retry-After)
    public static long retryAfterSeconds(ConsumptionProbe probe) {
        long nanos = probe.getNanosToWaitForRefill();
        return Math.max(1L, (nanos + 999_999_999L) / 1_000_000_000L);
    }

    // Vide tous les seaux. Utilisé par les tests (RateLimitResetListener) entre chaque méthode
    public void reset() {
        buckets.invalidateAll();
    }

    private static Bucket perHour(int capacity) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, Duration.ofHours(1)))
                .build();
    }
}
