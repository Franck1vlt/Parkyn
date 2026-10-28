package app.parkyn.email;

import java.util.Map;

/**
 * Demande d'envoi d'un email, à publier avec ApplicationEventPublisher.
 * L'envoi n'a lieu qu'après le commit de la transaction en cours (EmailEventListener).
 */
public record EmailRequest(
    String to,
    String subject,
    String template,
    Map<String, Object> variables
) {

    // L'adresse et les variables (liens avec jeton) ne doivent pas apparaître dans un log
    @Override
    public String toString() {
        return "EmailRequest[template=" + template + "]";
    }
}
