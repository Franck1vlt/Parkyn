package app.parkyn.security.authorization;

import org.springframework.stereotype.Component;

import app.parkyn.model.User;

@Component
public class UserAccessPolicy {

    // Contrôle sur l'objet : l'acteur peut-il faire cette action sur CET utilisateur ?
    public AccessDecision decide(User actor, UserAction action, User target) {
        return switch (actor.getRole()) {
            case ADMIN -> AccessDecision.GRANTED;
            case AGENT -> {
                if (target.getRole() == User.Role.DRIVER || action == UserAction.READ) {
                    yield AccessDecision.GRANTED;
                }
                yield new AccessDecision.Denied("Un agent ne peut modifier que des comptes conducteur");
            }
            case DRIVER -> new AccessDecision.Denied("Un conducteur ne peut pas gérer les comptes");
        };
    }

    // Anti-escalade : l'acteur peut-il attribuer ce rôle ? (création et modification)
    public AccessDecision decideRoleAssignment(User actor, User.Role requestedRole) {
        if (requestedRole == null) {
            return AccessDecision.GRANTED; // le rôle ne change pas : rien n'est attribué
        }
        return switch (actor.getRole()) {
            case ADMIN -> AccessDecision.GRANTED;
            case AGENT -> requestedRole == User.Role.DRIVER
                ? AccessDecision.GRANTED
                : new AccessDecision.Denied("Un agent ne peut attribuer que le rôle conducteur");
            case DRIVER -> new AccessDecision.Denied("Un conducteur ne peut pas attribuer de rôle");
        };
    }
}
