package app.parkyn.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Refus d'un refresh après une révocation voulue (session expirée, compte non actif).
 * Déclarée en noRollbackFor sur AuthService.refresh : la révocation est validée malgré le 401.
 * À ne lever qu'après des écritures qui doivent être conservées.
 */
public class SessionRejectedException extends ResponseStatusException {

    public SessionRejectedException(String reason) {
        super(HttpStatus.UNAUTHORIZED, reason);
    }
}
