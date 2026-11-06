package app.parkyn.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;

import app.parkyn.dto.ValidationErrorResponse;

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
    * Validation @Valid échouée (ex: email non valide, mot de passe trop court, etc...)
    * @return - 400 Bad Request, JSON { "errors": { "champ": ["message", ...] } }
    */
   @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        BindingResult binding = ex.getBindingResult();
        Map<String, List<String>> errors = new TreeMap<>();
        for (FieldError error : binding.getFieldErrors()) {
            errors.computeIfAbsent(jsonName(binding.getTarget(), error.getField()), key -> new ArrayList<>())
                .add(error.getDefaultMessage());
        }
        for (ObjectError error : binding.getGlobalErrors()) {
            errors.computeIfAbsent(ValidationErrorResponse.GLOBAL_KEY, key -> new ArrayList<>())
                .add(error.getDefaultMessage());
        }
        // Bean Validation ne garantit pas l'ordre des violations : tri pour une réponse stable
        errors.values().forEach(Collections::sort);
        return ResponseEntity.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ValidationErrorResponse(errors));
    }

    // Nom JSON du champ (@JsonProperty) ; à défaut, le nom Java (aussi pour un chemin imbriqué)
    private String jsonName(Object target, String field) {
        if (target == null) {
            return field;
        }
        try {
            JsonProperty annotation = target.getClass().getDeclaredField(field).getAnnotation(JsonProperty.class);
            return annotation == null || annotation.value().isEmpty() ? field : annotation.value();
        } catch (NoSuchFieldException e) {
            return field;
        }
    }
}
