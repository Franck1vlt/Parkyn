package app.parkyn.email;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import app.parkyn.IntegrationTest;
import jakarta.mail.Message;

@IntegrationTest 
public class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @Value("${parkyn.mail.from}")
    private String from;

    @RegisterExtension 
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    // Test de l'envoi d'un email avec GreenMail
    @Test
    public void sendsEmailToRecipient() throws Exception {

        emailService.send("driver@parkyn.app", "Sujet", "Corps");

        Message[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length);
        assertEquals("driver@parkyn.app", messages[0].getAllRecipients()[0].toString());
        assertEquals("Sujet", messages[0].getSubject());
    }

    // Test de l'expéditeur configuré dans application.properties
    @Test 
    public void senderIsConfigured() throws Exception {

        emailService.send("driver@parkyn.app", "Sujet", "Corps");

        Message[] messages = greenMail.getReceivedMessages();
        assertEquals(from, messages[0].getFrom()[0].toString());
    }

    // Test du corps du message
    @Test 
    public void bodyIsSent() throws Exception {

        emailService.send("driver@parkyn.app", "Sujet", "Corps");

        Message[] messages = greenMail.getReceivedMessages();
        assertEquals("Corps", GreenMailUtil.getBody(messages[0]).trim());
    }

}
