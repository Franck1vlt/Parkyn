package app.parkyn.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mail.MailPreparationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import app.parkyn.IntegrationTest;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
public class EmailEventTest {

    private static final String RECIPIENT = "recipient@example.com";
    private static final String SUBJECT = "Sujet";
    private static final String TEMPLATE_NAME = "test";
    private static final Map<String, Object> VARIABLES = Map.of("name", "Parkyn", "link", "http://localhost/verify?token=abc");
    private static final EmailRequest EVENT = new EmailRequest(RECIPIENT, SUBJECT, TEMPLATE_NAME, VARIABLES);

    // On teste le branchement de l'événement, pas l'envoi : le service est un mock
    @MockitoBean EmailService emailService;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate transactionTemplate;

    // L'email part après le commit, jamais pendant la transaction
    @Test
    public void emailIsSentAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> {
            publisher.publishEvent(EVENT);
            verify(emailService, never()).send(anyString(), anyString(), anyString(), anyMap());
        });

        verify(emailService).send(RECIPIENT, SUBJECT, TEMPLATE_NAME, VARIABLES);
    }

    // Une transaction annulée n'envoie rien
    @Test
    public void noEmailOnRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            publisher.publishEvent(EVENT);
            status.setRollbackOnly();
        });

        verify(emailService, never()).send(anyString(), anyString(), anyString(), anyMap());
    }

    // Hors transaction, l'email part immédiatement (fallbackExecution)
    @Test
    public void emailIsSentWithoutTransaction() {
        publisher.publishEvent(EVENT);

        verify(emailService).send(RECIPIENT, SUBJECT, TEMPLATE_NAME, VARIABLES);
    }

    // Un échec après le commit (file pleine) est journalisé sans faire échouer l'appelant
    @Test
    public void sendFailureDoesNotFailCommit(CapturedOutput output) {
        doThrow(new TaskRejectedException("file pleine"))
            .when(emailService).send(RECIPIENT, SUBJECT, TEMPLATE_NAME, VARIABLES);

        assertDoesNotThrow(() -> transactionTemplate.executeWithoutResult(status -> publisher.publishEvent(EVENT)));

        assertTrue(output.getOut().contains("Échec de la demande d'email"), output.getOut());
    }

    // Une adresse invalide est citée par l'exception : elle ne doit pas finir dans les logs (RGPD)
    @Test
    public void sendFailureLogDoesNotLeakRecipient(CapturedOutput output) {
        doThrow(new MailPreparationException("Illegal address in string ``" + RECIPIENT + "''"))
            .when(emailService).send(RECIPIENT, SUBJECT, TEMPLATE_NAME, VARIABLES);

        publisher.publishEvent(EVENT);

        assertTrue(output.getOut().contains("Échec de la demande d'email"), output.getOut());
        assertFalse(output.getOut().contains(RECIPIENT), output.getOut());
    }
}
