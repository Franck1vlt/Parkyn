package app.parkyn.security.authorization;

/**
 * L'interface AccessDecision représente le résultat d'une décision d'accès dans le système de sécurité. 
 * Elle peut être implémentée par différentes classes pour représenter différents types de décisions, 
 * telles que l'accès accordé, l'accès refusé avec une raison spécifique, ou l'élément recherché non trouvé.
 */
public sealed interface AccessDecision {

    AccessDecision GRANTED = new Granted();
    AccessDecision NOT_FOUND = new NotFound();

    record Granted() implements AccessDecision {}

    record Denied(String reason) implements AccessDecision {
        public Denied {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be null or blank");
            }
        }
    }

    record NotFound() implements AccessDecision {}

}
