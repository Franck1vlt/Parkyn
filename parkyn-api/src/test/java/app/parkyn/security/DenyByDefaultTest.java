package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import app.parkyn.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;

@IntegrationTest 
class DenyByDefaultTest {

    @Autowired RequestMappingHandlerMapping handlerMapping;

    @Test 
    void everyEndpointIsProtectedOrExplicitlyPublic() {
        List<String> unprotected = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            // 1 : ignorer ce qui n'est pas à nous
            //          (handlerMethod.getBeanType().getPackageName() ne commence pas par "app.parkyn")
            if (!handlerMethod.getBeanType().getPackageName().startsWith("app.parkyn")) {
                return;
            }

            // 2 : récupérer la méthode Java : handlerMethod.getMethod()
            Method method = handlerMethod.getMethod();

            // 3 : protégé si @PreAuthorize sur la méthode OU sur sa classe (getBeanType())
            if (method.isAnnotationPresent(PreAuthorize.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PreAuthorize.class)) {
                return;
            }
            // 4 : ou public si @PublicEndpoint sur la méthode
            if (method.isAnnotationPresent(PublicEndpoint.class)) {
                return;
            }

            // 5 : sinon, ajouter à unprotected un libellé lisible,
            //          par exemple mapping + " -> " + nom de la classe + "." + nom de la méthode
            unprotected.add(mapping + " -> " + handlerMethod.getBeanType().getSimpleName() + "." + method.getName());
        });

        // 6 : une seule assertion, avec la liste complète dans le message
        //          (la liste doit être vide)
        assertTrue(unprotected.isEmpty(), "Endpoints non protégés : " + unprotected);
    }
}
