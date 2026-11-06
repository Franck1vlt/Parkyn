package app.parkyn;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.parkyn.dto.RegisterRequest;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.JwtService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Contrat des 400 de validation : { "errors": { "<champ JSON>": ["message", ...] } }, messages en français.
 * Le mapping nom Java -> nom JSON et la clé "_global" sont couverts par GlobalExceptionHandlerTest.
 */
@AutoConfigureMockMvc
@IntegrationTest
public class ValidationErrorTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private static final String FIRSTNAME = "John";
    private static final String LASTNAME = "Doe";
    private static final String EMAIL = "valid@parkyn.app";
    private static final String PASSWORD = "password123!";

    // Corps construit par Jackson : la 400 vient de la validation, pas d'un JSON illisible
    private String body(String firstname, String lastname, String email, String password) {
        return objectMapper.writeValueAsString(new RegisterRequest(firstname, lastname, email, password));
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void invalidEmailIsReportedOnEmailOnly() throws Exception {
        register(body(FIRSTNAME, LASTNAME, "pas-un-email", PASSWORD))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errors", aMapWithSize(1)))
            .andExpect(jsonPath("$.errors.email", hasSize(1)));
    }

    @ParameterizedTest(name = "mot de passe de {0} caractères : 400 sur password")
    @MethodSource("badPasswordLengths")
    void passwordLengthIsReportedOnPasswordOnly(int length) throws Exception {
        register(body(FIRSTNAME, LASTNAME, EMAIL, "a".repeat(length)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", aMapWithSize(1)))
            .andExpect(jsonPath("$.errors.password", hasSize(1)))
            .andExpect(jsonPath("$.errors.password[0]", containsString("taille")));
    }

    private static Stream<Arguments> badPasswordLengths() {
        return Stream.of(Arguments.of(11), Arguments.of(129));
    }

    @ParameterizedTest(name = "{0} avec caractère interdit : 400 sur {0}")
    @MethodSource("forbiddenNames")
    void forbiddenCharactersInNameAreReportedOnThatField(String field, String firstname, String lastname)
            throws Exception {
        register(body(firstname, lastname, EMAIL, PASSWORD))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", aMapWithSize(1)))
            .andExpect(jsonPath("$.errors." + field + "[0]").value("contient des caractères non autorisés"));
    }

    private static Stream<Arguments> forbiddenNames() {
        return Stream.of(
            Arguments.of("firstname", "Jean2", LASTNAME),
            Arguments.of("lastname", FIRSTNAME, "Dupont3"));
    }

    @ParameterizedTest(name = "{0} vide : clé présente")
    @MethodSource("emptyFields")
    void emptyFieldIsReported(String field, String firstname, String lastname, String email, String password)
            throws Exception {
        register(body(firstname, lastname, email, password))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors." + field).isNotEmpty());
    }

    private static Stream<Arguments> emptyFields() {
        return Stream.of(
            Arguments.of("firstname", "", LASTNAME, EMAIL, PASSWORD),
            Arguments.of("lastname", FIRSTNAME, "", EMAIL, PASSWORD),
            Arguments.of("email", FIRSTNAME, LASTNAME, "", PASSWORD),
            Arguments.of("password", FIRSTNAME, LASTNAME, EMAIL, ""));
    }

    @Test
    void severalInvalidFieldsAreAllReported() throws Exception {
        register(body("Jean2", "Dupont3", "pas-un-email", "court"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors", aMapWithSize(4)))
            .andExpect(jsonPath("$.errors.firstname").isNotEmpty())
            .andExpect(jsonPath("$.errors.lastname").isNotEmpty())
            .andExpect(jsonPath("$.errors.email").isNotEmpty())
            .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    // Mot de passe vide : @NotBlank et @Size(min = 12) échouent ensemble, messages triés
    @Test
    void severalMessagesOnOneFieldAreSorted() throws Exception {
        String response = register(body(FIRSTNAME, LASTNAME, EMAIL, ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password", hasSize(2)))
            .andReturn().getResponse().getContentAsString();

        List<String> messages = new ArrayList<>();
        for (JsonNode message : objectMapper.readTree(response).path("errors").path("password")) {
            messages.add(message.asString());
        }
        assertEquals(messages.stream().sorted().toList(), messages);
    }

    @Test
    void bodyExposesNoInternalDetail() throws Exception {
        ResultActions result = register(body("Jean2", LASTNAME, "pas-un-email", "court"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists());

        for (String internal : List.of("Exception", "app.parkyn", "RegisterRequest", "stackTrace", "(?", "\\p{")) {
            result.andExpect(content().string(not(containsString(internal))));
        }
    }

    // La validation passe avant le service : un email déjà inscrit ne change pas la réponse
    @Test
    void validationDoesNotRevealExistingEmail() throws Exception {
        register(body(FIRSTNAME, LASTNAME, "existing@parkyn.app", PASSWORD)).andExpect(status().isAccepted());

        String invalidForExisting = register(body("Jean2", LASTNAME, "existing@parkyn.app", "court"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").exists())
            .andReturn().getResponse().getContentAsString();
        String invalidForUnknown = register(body("Jean2", LASTNAME, "unknown@parkyn.app", "court"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").exists())
            .andReturn().getResponse().getContentAsString();

        assertEquals(invalidForUnknown, invalidForExisting);
    }

    // La langue ne dépend pas du navigateur
    @Test
    void messagesStayInFrenchWhateverTheBrowserLanguage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(FIRSTNAME, LASTNAME, EMAIL, "court")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password[0]", containsString("taille")))
            .andExpect(jsonPath("$.errors.password[0]", not(containsString("size"))));
    }

    // Même format sur un autre endpoint @Valid
    @Test
    void otherValidatedEndpointUsesTheSameFormat() throws Exception {
        User admin = new TestUsers(userRepository, jdbcTemplate, passwordEncoder)
            .createWithoutPassword("admin@parkyn.app", User.Role.ADMIN);

        mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstname\":\"\",\"lastname\":\"Doe\",\"email\":\"pas-un-email\",\"password\":\"x\",\"active\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errors.firstname").isNotEmpty())
            .andExpect(jsonPath("$.errors.email").isNotEmpty())
            .andExpect(jsonPath("$.errors.role").isNotEmpty());
    }
}
