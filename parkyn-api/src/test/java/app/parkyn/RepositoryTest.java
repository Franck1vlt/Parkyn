package app.parkyn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import app.parkyn.tenant.CurrentTenantResolver;

/**
 * Permet de marquer une classe de test comme étant un test d'intégration de repository.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @DataJpaTest ne charge pas les @Component : le résolveur de tenant est importé pour que @TenantId fonctionne
@Import({TestcontainersConfig.class, CurrentTenantResolver.class})
public @interface RepositoryTest {

}
