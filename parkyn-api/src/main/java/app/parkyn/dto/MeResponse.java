package app.parkyn.dto;

import java.util.UUID;

import app.parkyn.model.User;

public record MeResponse(
    UUID id,
    String firstname,
    String lastname,
    String email,
    String role
) {
    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole().name());
    }
}
