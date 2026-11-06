package app.parkyn.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mapping nom Java -> nom JSON et erreurs globales : aucun DTO de requête de l'API n'a de @JsonProperty
 * ni de contrainte de classe, donc ce n'est pas testable de bout en bout.
 */
class GlobalExceptionHandlerTest {

    record Demo(@JsonProperty("first_name") String firstName, String lastname) {
    }

    @SuppressWarnings("unused")
    private void endpoint(Demo demo) {
    }

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MethodArgumentNotValidException exception(BeanPropertyBindingResult result) throws Exception {
        MethodParameter parameter = new MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("endpoint", Demo.class), 0);
        return new MethodArgumentNotValidException(parameter, result);
    }

    private JsonNode errorsOf(BeanPropertyBindingResult result) throws Exception {
        ResponseEntity<?> response = handler.handleValidation(exception(result));
        return objectMapper.readTree(objectMapper.writeValueAsString(response.getBody())).path("errors");
    }

    // La clé est le nom JSON (@JsonProperty), pas le nom de la propriété Java
    @Test
    void keyIsTheJsonNameOfTheField() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Demo(null, null), "demo");
        result.addError(new FieldError("demo", "firstName", "ne doit pas être vide"));

        JsonNode errors = errorsOf(result);

        assertEquals("ne doit pas être vide", errors.path("first_name").path(0).asString());
        assertEquals(0, errors.path("firstName").size());
    }

    // Sans @JsonProperty, le nom Java est le nom JSON
    @Test
    void keyFallsBackToTheJavaName() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Demo(null, null), "demo");
        result.addError(new FieldError("demo", "lastname", "ne doit pas être vide"));

        assertEquals("ne doit pas être vide", errorsOf(result).path("lastname").path(0).asString());
    }

    // Erreur d'objet sans champ : clé réservée
    @Test
    void objectErrorGoesUnderGlobalKey() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Demo(null, null), "demo");
        result.addError(new ObjectError("demo", "les dates sont incohérentes"));

        assertEquals("les dates sont incohérentes", errorsOf(result).path("_global").path(0).asString());
    }
}
