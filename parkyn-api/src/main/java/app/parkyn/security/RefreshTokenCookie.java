package app.parkyn.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";

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

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth/refresh");
    }
}

