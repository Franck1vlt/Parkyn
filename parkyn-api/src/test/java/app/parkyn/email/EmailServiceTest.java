package app.parkyn.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.thymeleaf.exceptions.TemplateInputException;

import app.parkyn.IntegrationTest;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

@IntegrationTest
public class EmailServiceTest {

    private static final String TEMPLATE_NAME = "test";
    private static final String RECIPIENT = "driver@parkyn.app";
    private static final String LINK = "http://localhost/verify?token=abc";
    private static final Map<String, Object> EMAIL_CONTEXT = Map.of("name", "Parkyn", "link", LINK);

    @Autowired
    private EmailService emailService;

    @Value("${parkyn.mail.from}")
    private String from;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    // Test de l'envoi d'un email avec GreenMail
    @Test
    public void sendsEmailToRecipient() throws Exception {
        emailService.send(RECIPIENT, "Sujet", TEMPLATE_NAME, EMAIL_CONTEXT);

        MimeMessage message = singleMessage();
        assertEquals(RECIPIENT, message.getAllRecipients()[0].toString());
        assertEquals("Sujet", message.getSubject());
    }

    // Test de l'expéditeur configuré dans application.properties
    @Test
    public void senderIsConfigured() throws Exception {
        emailService.send(RECIPIENT, "Sujet", TEMPLATE_NAME, EMAIL_CONTEXT);

        assertEquals(from, singleMessage().getFrom()[0].toString());
    }

    // Le message contient une version HTML et une version texte
    @Test
    public void htmlAndTextVersionsAreSent() throws Exception {
        emailService.send(RECIPIENT, "Sujet", TEMPLATE_NAME, EMAIL_CONTEXT);

        MimeMessage message = singleMessage();
        assertNotNull(partOf(message, "text/html"), "version HTML absente");
        assertNotNull(partOf(message, "text/plain"), "version texte absente");
    }

    // Les variables sont rendues dans les deux versions
    @Test
    public void variablesAreRendered() throws Exception {
        emailService.send(RECIPIENT, "Sujet", TEMPLATE_NAME, EMAIL_CONTEXT);

        MimeMessage message = singleMessage();
        String html = partOf(message, "text/html");
        String text = partOf(message, "text/plain");

        assertTrue(html.contains("Parkyn"), html);
        assertTrue(html.contains("href=\"" + LINK + "\""), html);
        assertTrue(text.contains("Bonjour Parkyn,"), text);
        assertTrue(text.contains(LINK), text);
    }

    // Une variable saisie par l'utilisateur ne peut pas injecter de HTML
    @Test
    public void htmlEscapesVariables() throws Exception {
        emailService.send(RECIPIENT, "Sujet", TEMPLATE_NAME,
            Map.of("name", "<script>alert(1)</script>", "link", LINK));

        String html = partOf(singleMessage(), "text/html");

        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), html);
        assertFalse(html.contains("<script>"), html);

        // La version texte est volontairement brute (mode TEXT) : text/plain n'interprète pas le HTML
        String text = partOf(singleMessage(), "text/plain");
        assertTrue(text.contains("<script>alert(1)</script>"), text);
    }

    // Le sujet, les variables et le texte fixe gardent leurs accents
    @Test
    public void accentsAreKept() throws Exception {
        String subject = "Vérifiez votre adresse";

        emailService.send(RECIPIENT, subject, TEMPLATE_NAME, Map.of("name", "Élise", "link", LINK));

        MimeMessage message = singleMessage();
        assertEquals(subject, message.getSubject());

        String html = partOf(message, "text/html");
        String text = partOf(message, "text/plain");
        assertTrue(html.contains("Élise") && html.contains("été prise en compte"), html);
        assertTrue(text.contains("Bonjour Élise,") && text.contains("été prise en compte"), text);

        // Contrat : l'email déclare l'UTF-8. Sur une JVM en UTF-8 par défaut (Java 18+), ces assertions
        // passent aussi sans l'encodage explicite d'EmailService : elles documentent, sans le protéger
        // contre un oubli (vérifié par mutation)
        assertTrue(message.getHeader("Subject")[0].startsWith("=?UTF-8?"), message.getHeader("Subject")[0]);
        assertTrue(findPart(message, "text/html").getContentType().toUpperCase().contains("CHARSET=UTF-8"));
        assertTrue(findPart(message, "text/plain").getContentType().toUpperCase().contains("CHARSET=UTF-8"));
    }

    // Le paramètre template choisit bien les fichiers rendus
    @Test
    public void templateNameSelectsFiles() throws Exception {
        emailService.send(RECIPIENT, "Sujet", "other", EMAIL_CONTEXT);

        MimeMessage message = singleMessage();
        assertTrue(partOf(message, "text/html").contains("Autre template HTML"));
        assertTrue(partOf(message, "text/plain").contains("Autre template texte"));
    }

    // Un template introuvable lève une exception avant tout envoi
    @Test
    public void missingTemplateSendsNothing() {
        assertThrows(TemplateInputException.class,
            () -> emailService.send(RECIPIENT, "Sujet", "missing", EMAIL_CONTEXT));

        assertEquals(0, greenMail.getReceivedMessages().length);
    }


    // --- Aides ---

    // Vérifie qu'un seul email est parti et le renvoie
    private MimeMessage singleMessage() throws Exception {
        assertTrue(greenMail.waitForIncomingEmail(5000, 1), "email non reçu dans le délai imparti");
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "nombre d'emails reçus");
        return messages[0];
    }

    // Contenu de la partie MIME du type demandé, ou null si elle est absente
    private String partOf(Part part, String mimeType) throws Exception {
        Part found = findPart(part, mimeType);
        return found == null ? null : found.getContent().toString();
    }

    // Cherche récursivement la partie MIME du type demandé (les parties sont imbriquées)
    private Part findPart(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return part;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                Part result = findPart(multipart.getBodyPart(i), mimeType);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

}
