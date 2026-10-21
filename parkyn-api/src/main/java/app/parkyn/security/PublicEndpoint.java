package app.parkyn.security;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

/**
 * une phrase. Marque un endpoint volontairement accessible sans authentification
 * (connexion, inscription...). Vérifié par le test « deny by default ».
 */
@Target ({ ElementType.METHOD, ElementType.TYPE })
@Retention (RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
