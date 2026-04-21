package app.parkyn.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
    @JsonProperty("access_token")       String accessToken,
    @JsonProperty("expired_at")         OffsetDateTime expiredAt,
    @JsonProperty("refresh_token")      String refreshToken,
    @JsonProperty("refresh_expired_at") OffsetDateTime refreshExpiredAt,
    @JsonProperty("token_type")         String tokenType
) {
    public LoginResponse(String accessToken, OffsetDateTime expiredAt,
                         String refreshToken, OffsetDateTime refreshExpiredAt) {
        this(accessToken, expiredAt, refreshToken, refreshExpiredAt, "Bearer");
    }
}
