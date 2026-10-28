package app.parkyn.email;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import app.parkyn.IntegrationTest;

@IntegrationTest
public class EmailExecutorTest {

    @Autowired ApplicationContext context;

    // Déclarer emailExecutor ne doit pas supprimer l'exécuteur par défaut de Spring Boot
    // (sinon un @Async sans nom ou l'async MVC tomberaient sur un exécuteur sans limite)
    @Test
    public void defaultTaskExecutorIsKept() {
        assertTrue(context.containsBean("emailExecutor"));
        assertTrue(context.containsBean("applicationTaskExecutor"));
    }
}
