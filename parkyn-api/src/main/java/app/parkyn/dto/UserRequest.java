package app.parkyn.dto;

import app.parkyn.model.User.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequest(
    @NotBlank String firstname,
    @NotBlank String lastname,
    @NotBlank @Email String email,
    @NotBlank String password,
    @NotNull Role role,
    @NotNull boolean active
) {
}
