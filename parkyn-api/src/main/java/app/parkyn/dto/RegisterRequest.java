package app.parkyn.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(max = 50) @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String firstname,
    @NotBlank @Size(max = 50) @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String lastname,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 128) String password
) {
    private static final String NAME_PATTERN = "^(?=.*\\p{L})[\\p{L}\\p{M}'\u2019 -]+$";
    private static final String NAME_MESSAGE = "contient des caractères non autorisés";
}
