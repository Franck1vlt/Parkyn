package app.parkyn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.springframework.test.context.jdbc.Sql;

/**
 * Permet d'avoir un test d'intégration avec Spring Boot et Testcontainers.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest 
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
// MERGE_WITH_DEFAULTS : sans lui, les listeners par défaut (@Sql, injection, @MockitoSpyBean...) seraient remplacés
@TestExecutionListeners(listeners = RateLimitResetListener.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
public @interface IntegrationTest {
}