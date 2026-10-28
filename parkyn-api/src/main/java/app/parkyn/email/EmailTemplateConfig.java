package app.parkyn.email;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Resolver des versions texte des emails (templates/email/*.txt).
 * Spring Boot l'ajoute au moteur Thymeleaf à côté de son resolver HTML (templates/*.html).
 */
@Configuration
public class EmailTemplateConfig {

    @Bean
    public ITemplateResolver textTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        // Le nom demandé porte déjà l'extension : seul un nom en .txt est traité ici,
        // les autres passent au resolver HTML de Spring Boot
        resolver.setResolvablePatterns(Set.of("*.txt"));
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        return resolver;
    }
}
