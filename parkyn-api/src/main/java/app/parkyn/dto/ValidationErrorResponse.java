package app.parkyn.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

// 400 de validation : messages par champ JSON ; les erreurs sans champ sont sous la clé "_global"
public record ValidationErrorResponse(
    @JsonProperty("errors") Map<String, List<String>> errors
) {
    public static final String GLOBAL_KEY = "_global";
}
