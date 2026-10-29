package app.parkyn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

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
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    private static final String FIRSTNAME = "John";
    private static final String LASTNAME = "Doe";
    private static final String ACCEPTED_EMAIL = "accepted@parkyn.app";
    private static final String PENDING_EMAIL = "pending@parkyn.app";
    private static final String CANNOT_LOGIN_EMAIL = "cannot-login@parkyn.app";
    private static final String DRIVER_EMAIL = "driver@parkyn.app";
    private static final String PASSWORD = "password123!";

    private String registerBody(String email, String firstname, String lastname, String password) {
        return String.format(
            "{\"email\":\"%s\", \"firstname\":\"%s\", \"lastname\":\"%s\", \"password\":\"%s\"}",
            email, firstname, lastname, password
        );
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
}
