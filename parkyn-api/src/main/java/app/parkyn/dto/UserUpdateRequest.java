package app.parkyn.dto;

import app.parkyn.model.User.Role;

public record UserUpdateRequest(
    String firstname,
    String lastname,
    String email,
    String password,
    Role role,
    Boolean active
) {}
