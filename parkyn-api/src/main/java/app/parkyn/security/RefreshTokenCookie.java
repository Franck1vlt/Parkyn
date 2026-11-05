package app.parkyn.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    private static final String LEGACY_PATH = "/api/v1/auth/refresh";

    private final Duration maxAge;

    public RefreshTokenCookie(@Value("${jwt.refresh-expiration-days}") long refreshDays) {
        this.maxAge = Duration.ofDays(refreshDays);
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    // Transition C4 : efface le cookie émis avant C4 (Path=/api/v1/auth/refresh). Plus spécifique, il serait
    // envoyé en premier sur /refresh et masquerait le nouveau (refresh en 401). À retirer au Contract
    public ResponseCookie clearLegacy() {
        return base("").path(LEGACY_PATH).maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                // /api/v1/auth et pas seulement /refresh : le logout et la liste des sessions identifient l'appareil courant (C4)
                .path("/api/v1/auth");
    }
}

