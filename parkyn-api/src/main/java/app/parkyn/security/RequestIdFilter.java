package app.parkyn.security;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Identifiant de requête (X-Request-Id) : repris de nginx s'il est sûr, sinon généré. Placé avant Spring Security
 * pour être sur toutes les réponses (401, 403, 429 compris). Il va dans le MDC des logs, avec le tenant et
 * l'utilisateur (posés par JwtAuthFilter). Chaque réponse 4xx/5xx laisse une ligne ERROR : méthode, chemin, statut.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String REQUEST_ID = "requestId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

    // Hexadécimal de nginx ($request_id) ou UUID ; rien d'autre n'entre dans les logs
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String id = requestId(request);
        MDC.put(REQUEST_ID, id);
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 400) {
                // Chemin seul : jamais la query string
                log.error("{} {} -> {}", request.getMethod(), request.getRequestURI(), response.getStatus());
            }
        } catch (ServletException | IOException | RuntimeException e) {
            // Exception non gérée (500) : seulement la classe, un message peut contenir des données (email, SQL)
            log.error("{} {} -> 500 ({})", request.getMethod(), request.getRequestURI(), e.getClass().getName());
            throw e;
        } finally {
            // Le MDC est lié au thread, réutilisé pour la requête suivante : toujours le vider
            MDC.remove(REQUEST_ID);
            MDC.remove(TENANT_ID);
            MDC.remove(USER_ID);
        }
    }

    private static String requestId(HttpServletRequest request) {
        String received = request.getHeader(HEADER);
        return received != null && SAFE_ID.matcher(received).matches() ? received : UUID.randomUUID().toString();
    }
}
