package app.parkyn;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import app.parkyn.security.RateLimiter;

/**
 * Vide les seaux de limitation de débit avant chaque test, comme cleanup.sql vide la base :
 * toute la suite partage le même contexte et la même IP (127.0.0.1).
 */
public class RateLimitResetListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        testContext.getApplicationContext().getBean(RateLimiter.class).reset();
    }
}
