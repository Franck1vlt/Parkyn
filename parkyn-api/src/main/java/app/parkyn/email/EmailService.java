package app.parkyn.email;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;


@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailDispatcher mailDispatcher;
    private final TemplateEngine templateEngine;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, MailDispatcher mailDispatcher, TemplateEngine templateEngine,
                        @Value("${parkyn.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.mailDispatcher = mailDispatcher;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
    }

    /**
     * Envoie un email en deux versions, HTML et texte, rendues depuis
     * templates/email/{template}.html et templates/email/{template}.txt
     * <p>
     * Visibilité package : les services métier publient un {@link EmailRequest}, pour que l'email
     * ne parte qu'après le commit (EmailEventListener) et jamais pour une transaction annulée.
     */
    void send(String to, String subject, String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        String html = templateEngine.process("email/" + template, context);
        String text = templateEngine.process("email/" + template + ".txt", context);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
        } catch (MessagingException e) {
            throw new MailPreparationException("Préparation de l'email impossible", e);
        }
        mailDispatcher.dispatch(message);
    }

}
