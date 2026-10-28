package app.parkyn.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class EmailRequestTest {

    // Un log de l'événement ne doit exposer ni l'adresse, ni le lien qui porte le jeton
    @Test
    public void toStringHidesPersonalData() {
        EmailRequest request = new EmailRequest("driver@parkyn.app", "Sujet", "verify",
            Map.of("link", "http://localhost/verify?token=secret"));

        String text = request.toString();

        assertFalse(text.contains("driver@parkyn.app"), text);
        assertFalse(text.contains("secret"), text);
        assertTrue(text.contains("verify"), text);
    }
}
