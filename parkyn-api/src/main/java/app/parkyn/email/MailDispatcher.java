package app.parkyn.email;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component 
public class MailDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailDispatcher.class);

    private final JavaMailSender mailSender;

    public MailDispatcher(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async("emailExecutor")
    public void dispatch(MimeMessage message) {
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // Le message de l'exception peut citer l'adresse (réponse SMTP) : seul son type est journalisé.
            // La trace complète reste disponible en DEBUG, à n'activer que ponctuellement
            LOGGER.error("Échec de l'envoi de l'email au domaine {} ({})",
                    recipientDomains(message), exception.getClass().getSimpleName());
            LOGGER.debug("Détail de l'échec d'envoi", exception);
        }
    }

    private String recipientDomains(MimeMessage message) {
        try {
            Address[] recipients = message.getAllRecipients();
            if (recipients == null || recipients.length == 0) {
                return "inconnu";
            }

            StringBuilder domains = new StringBuilder();
            for (Address recipient : recipients) {
                if (recipient instanceof InternetAddress internetAddress) {
                    String address = internetAddress.getAddress();
                    int separator = address == null ? -1 : address.lastIndexOf('@');
                    String domain = separator >= 0 ? address.substring(separator + 1) : "inconnu";
                    if (domains.length() > 0) {
                        domains.append(",");
                    }
                    domains.append(domain);
                }
            }
            return domains.length() == 0 ? "inconnu" : domains.toString();
        } catch (MessagingException exception) {
            return "inconnu";
        }
    }

}
