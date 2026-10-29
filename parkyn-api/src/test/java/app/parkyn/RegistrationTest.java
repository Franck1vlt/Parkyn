package app.parkyn;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import app.parkyn.dto.RegisterRequest;
import app.parkyn.email.EmailRequest;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.tenant.Tenants;

@AutoConfigureMockMvc 
@IntegrationTest 
@RecordApplicationEvents
public class RegistrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ApplicationEvents applicationEvents;
    @Autowired ObjectMapper objectMapper;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    private static final String FIRSTNAME = "John";
    private static final String LASTNAME = "Doe";
    private static final String ACCEPTED_EMAIL = "accepted@parkyn.app";
    private static final String PENDING_EMAIL = "pending@parkyn.app";
    private static final String CANNOT_LOGIN_EMAIL = "cannot-login@parkyn.app";
    private static final String DRIVER_EMAIL = "driver@parkyn.app";
    private static final String PASSWORD = "password123!";

    // Email valide pour @Email (partie locale de 64, labels de 63 au plus) mais de 255 caractères au total :
    // seul un @Size(max = 254) le refuse. 64 + 1 + 190 = 255
    private static final String EMAIL_255 = "a".repeat(64) + "@"
            + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".app";
    private static final String EMAIL_254 = "a".repeat(64) + "@"
            + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".app";

    // Corps construit par Jackson : un \n ou un " dans un champ reste du JSON valide,
    // sinon la 400 viendrait d'un JSON illisible et non de la validation
    private String registerBody(String email, String firstname, String lastname, String password) {
        return objectMapper.writeValueAsString(new RegisterRequest(firstname, lastname, email, password));
    }
    private String registerBody(String email) {
        return registerBody(email, FIRSTNAME, LASTNAME, PASSWORD);
    }

    // Test d'une inscription réussie
    @Test 
    public void registerReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));
    }

    // Test d'une inscription avec un email non vérifié
    @Test 
    public void newAccountIsPending() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(PENDING_EMAIL)));

        User user = userRepository.findByEmail(PENDING_EMAIL).orElseThrow();
        assertEquals(User.Status.PENDING_EMAIL_VERIFICATION, user.getStatus());
    }

    // Test d'une inscription avec un email qui ne peut pas se connecter
    @Test 
    public void newAccountCannotLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(CANNOT_LOGIN_EMAIL)));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"" + CANNOT_LOGIN_EMAIL + "\", \"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // Test d'une inscription avec un email qui devient un DRIVER dans le tenant par défaut
    @Test 
    public void newAccountIsDriverInDefaultTenant() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(DRIVER_EMAIL)));

        User user = userRepository.findByEmail(DRIVER_EMAIL).orElseThrow();
        assertEquals(User.Role.DRIVER, user.getRole());
        assertEquals(Tenants.DEFAULT_ID, user.getTenantId());
    }

    // Test d'une inscription avec un email déjà utilisé
    @Test 
    public void duplicateEmailReturnsSameResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));
        
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));
    }

    // Test d'une inscription avec un email déjà utilisé, ne modifie pas le compte existant
    @Test 
    public void duplicateEmailKeepsExistingAccount() throws Exception {
        String EXISTING_EMAIL = "existing@parkyn.app";
        TestUsers testUsers = new TestUsers(userRepository, jdbcTemplate, passwordEncoder);
        testUsers.create(EXISTING_EMAIL, PASSWORD, User.Role.DRIVER);

        // Deuxième inscription avec un autre mot de passe et d'autres noms
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
            .content(registerBody(EXISTING_EMAIL, "Other", "Name", "otherPassword123!")))
            .andExpect(status().isAccepted());

        // Vérification que l'ancien mot de passe fonctionne toujours
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
            .content("{\"email\":\"" + EXISTING_EMAIL + "\", \"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk());

        User existing = userRepository.findByEmail(EXISTING_EMAIL).orElseThrow();
        assertEquals("Test", existing.getFirstName());
    }

    // Test d'une inscription avec un email déjà utilisé, mais normalisé (ex: majuscules)
    @Test 
    public void duplicateEmailIsNormalized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody("Accepted@parkyn.app")))
            .andExpect(status().isAccepted());

        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, ACCEPTED_EMAIL));
    }

    // Test d'une inscription avec un email déjà utilisé, le mot de passe est toujours hashé même si l'inscription échoue
    @Test 
    public void duplicateEmailStillHashesPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted());

        clearInvocations(passwordEncoder);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted());

        verify(passwordEncoder, times(1)).encode(PASSWORD);
    }

    // Test d'une inscription avec un email déjà utilisé, un email est envoyé au propriétaire du compte existant
    @Test 
    public void duplicateEmailWarnsOwner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(ACCEPTED_EMAIL)))
            .andExpect(status().isAccepted());

        List<EmailRequest> warnings = applicationEvents.stream(EmailRequest.class)
                .filter(request -> "account-exists".equals(request.template()))
                .toList();

        assertEquals(1, warnings.size());
        assertEquals(ACCEPTED_EMAIL, warnings.get(0).to());
    }

    // Requête refusée par la validation : 400 et aucun compte créé
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    public void invalidRegistrationIsRejected(String label, String firstname, String lastname, String email,
            String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(email, firstname, lastname, password)))
            .andExpect(status().isBadRequest());

        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users WHERE email = ?", Integer.class, User.normalizeEmail(email)));
    }

    private static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
            Arguments.of("prénom de 51 caractères", "a".repeat(51), LASTNAME, "long-first@parkyn.app", PASSWORD),
            Arguments.of("prénom avec retour à la ligne", "Jean\nVotre compte est suspendu", LASTNAME,
                "line-break@parkyn.app", PASSWORD),
            Arguments.of("prénom avec retour chariot", "Jean\rVotre compte", LASTNAME, "carriage@parkyn.app", PASSWORD),
            Arguments.of("prénom avec http://", "Jean http://evil.example", LASTNAME, "http@parkyn.app", PASSWORD),
            Arguments.of("prénom avec HTTPS:// en majuscules", "Jean HTTPS://evil.example", LASTNAME,
                "https@parkyn.app", PASSWORD),
            Arguments.of("prénom avec www.", "Jean www.evil.example", LASTNAME, "www@parkyn.app", PASSWORD),
            Arguments.of("nom de 51 caractères", FIRSTNAME, "a".repeat(51), "long-last@parkyn.app", PASSWORD),
            Arguments.of("nom avec retour à la ligne", FIRSTNAME, "Doe\nAppelez le 01 23 45 67 89",
                "last-line-break@parkyn.app", PASSWORD),
            Arguments.of("nom avec www.", FIRSTNAME, "Doe www.evil.example", "last-www@parkyn.app", PASSWORD),
            // Garde-fou si le point entre un jour dans les caractères autorisés : un domaine reste un lien
            Arguments.of("prénom avec un domaine", "Jean evil.example", LASTNAME, "domain@parkyn.app", PASSWORD),
            Arguments.of("prénom avec chiffres", "Jean2", LASTNAME, "digits@parkyn.app", PASSWORD),
            Arguments.of("prénom sans aucune lettre", "' - '", LASTNAME, "no-letter@parkyn.app", PASSWORD),
            Arguments.of("email de 255 caractères", FIRSTNAME, LASTNAME, EMAIL_255, PASSWORD),
            Arguments.of("mot de passe de 11 caractères", FIRSTNAME, LASTNAME, "short-pwd@parkyn.app", "a".repeat(11)),
            Arguments.of("mot de passe de 129 caractères", FIRSTNAME, LASTNAME, "long-pwd@parkyn.app", "a".repeat(129))
        );
    }

    // Garde-fou : les valeurs limites et les noms composés restent acceptés (vert avant et après le code)
    @ParameterizedTest(name = "{0}")
    @MethodSource("validRegistrations")
    public void validRegistrationIsAccepted(String label, String firstname, String lastname, String email,
            String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody(email, firstname, lastname, password)))
            .andExpect(status().isAccepted());

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT count(*) FROM users WHERE email = ?", Integer.class, User.normalizeEmail(email)));
    }

    private static Stream<Arguments> validRegistrations() {
        return Stream.of(
            Arguments.of("prénom et nom de 50 caractères", "a".repeat(50), "b".repeat(50), "boundary-names@parkyn.app",
                PASSWORD),
            Arguments.of("email de 254 caractères", FIRSTNAME, LASTNAME, EMAIL_254, PASSWORD),
            Arguments.of("mot de passe de 12 caractères", FIRSTNAME, LASTNAME, "pwd-12@parkyn.app", "a".repeat(12)),
            Arguments.of("mot de passe de 128 caractères", FIRSTNAME, LASTNAME, "pwd-128@parkyn.app", "a".repeat(128)),
            Arguments.of("prénom avec trait d'union", "Jean-Pierre", LASTNAME, "hyphen@parkyn.app", PASSWORD),
            Arguments.of("nom avec apostrophe", FIRSTNAME, "O'Connor", "apostrophe@parkyn.app", PASSWORD),
            Arguments.of("prénom avec espace", "Anne Marie", LASTNAME, "space@parkyn.app", PASSWORD),
            Arguments.of("prénom accentué", "Éloïse", "Lefèvre", "accents@parkyn.app", PASSWORD),
            // Apostrophe typographique : celle des claviers iPhone et Mac par défaut
            Arguments.of("nom avec apostrophe typographique", FIRSTNAME, "O’Connor", "typographic@parkyn.app",
                PASSWORD),
            Arguments.of("nom vietnamien", FIRSTNAME, "Nguyễn", "vietnamese@parkyn.app", PASSWORD),
            Arguments.of("prénom polonais", "Łukasz", LASTNAME, "polish@parkyn.app", PASSWORD),
            Arguments.of("nom avec ligature", FIRSTNAME, "Œuvray", "ligature@parkyn.app", PASSWORD),
            // Accent en caractère séparé (forme décomposée NFD), produite par certains claviers et copier-coller
            Arguments.of("prénom avec accent combinant", "José", LASTNAME, "combining@parkyn.app", PASSWORD)
        );
    }

    // Le message d'erreur ne doit pas renvoyer l'expression régulière au client
    @Test
    public void validationErrorDoesNotExposePattern() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content(registerBody("pattern@parkyn.app", "Jean2", LASTNAME, PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(not(containsString("(?"))))
            .andExpect(content().string(not(containsString("\\p{"))))
            .andExpect(content().string(containsString("caractères non autorisés")));
    }
}
