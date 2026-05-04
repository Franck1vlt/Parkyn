package app.parkyn.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import app.parkyn.model.User.Role;
import jakarta.validation.constraints.Email;
import app.parkyn.model.User;

public record UserResponse(
    UUID id,
    String firstname,
    String lastname,
    @Email String email,
    Role role,
    boolean active,
    OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getRole(),
            user.isActive(),
            user.getCreatedAt()
        );
    }

}
