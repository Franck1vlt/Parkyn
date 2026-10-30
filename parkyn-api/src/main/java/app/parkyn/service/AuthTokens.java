package app.parkyn.service;

import app.parkyn.dto.LoginResponse;

// Résultat interne : le refresh token part dans un cookie, jamais dans le JSON
public record AuthTokens(LoginResponse body, String refreshToken) {

    // Masque le token si l'objet finit un jour dans un log
    @Override
    public String toString() {
        return "AuthTokens[body=" + body + ", refreshToken=***]";
    }
}
