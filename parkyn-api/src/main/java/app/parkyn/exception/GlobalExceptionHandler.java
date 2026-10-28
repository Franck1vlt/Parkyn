package app.parkyn.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
    * Mauvais crendentials (email ou mot de passe incorrect)
    * @return - 401 Unauthorized
    */
   @ExceptionHandler(BadCredentialsException.class)
   public ResponseEntity<String> handleBadCredentials(BadCredentialsException e) {
       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email ou mot de passe incorrect");
   }

    /**
    * Compte désactivé ou verrouillé
    * @return - 401 Unauthorized
    */
   @ExceptionHandler(AccountStatusException.class)
   public ResponseEntity<String> handleAccountStatus(AccountStatusException e) {
       return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email ou mot de passe incorrect");
   }

    /**
    * Token invalide ou expiré
    * @return - 401 Unauthorized
    */
   @ExceptionHandler(ResponseStatusException.class)
   public ResponseEntity<String> handleResponseStatus(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
}

    /**
    * Validation @Valid échouéee (ex: email non valide,² mot de passe trop court, etc...)
    * @return - 400 Bad Request
    */
   @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + " : " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(message);
    }
}
