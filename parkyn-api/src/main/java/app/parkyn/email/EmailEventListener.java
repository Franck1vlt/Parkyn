package app.parkyn.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie les emails demandés une fois la transaction validée : un rollback n'envoie rien.
 * Hors transaction, l'envoi est immédiat (fallbackExecution).
 */
@Component
public class EmailEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailEventListener.class);

    private final EmailService emailService;

    public EmailEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailRequest(EmailRequest request) {
        try {
            emailService.send(request.to(), request.subject(), request.template(), request.variables());
        } catch (RuntimeException exception) {
            // Les données sont déjà validées : une exception ici (file pleine, template) donnerait
            // une 500 pour une action réussie. On journalise, le renvoi (B3) sert de filet
            // Seul le type de l'exception est journalisé : son message peut citer l'adresse
            LOGGER.error("Échec de la demande d'email (template {}, {})",
                    request.template(), exception.getClass().getSimpleName());
            LOGGER.debug("Détail de l'échec de la demande d'email", exception);
        }
    }
}
