package app.parkyn.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

// Une session active = une famille (un appareil). id = family_id, stable entre deux refresh
public record SessionResponse(
    @JsonProperty("id")             UUID id,
    @JsonProperty("user_agent")     String userAgent,
    @JsonProperty("ip")             String ip,
    @JsonProperty("signed_in_at")   OffsetDateTime signedInAt,
    @JsonProperty("last_active_at") OffsetDateTime lastActiveAt,
    @JsonProperty("current")        boolean current
) {}
