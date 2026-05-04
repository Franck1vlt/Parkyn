package app.parkyn.dto;

import java.util.UUID;

import app.parkyn.model.User;
import app.parkyn.model.User.Role;

public record MeResponse(
    UUID id,
    String firstname,
    String lastname,
    String email,
    Role role
) {
    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole());
    }
}
