package app.parkyn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
    @NotBlank String firstname,
    @NotBlank String lastname,
    @NotBlank @Email String email,
    @NotBlank String password
) {}
