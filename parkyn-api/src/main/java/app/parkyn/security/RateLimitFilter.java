package app.parkyn.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// Limite par IP avant toute autre étape (sécurité, lecture du JSON, Argon2id) : une requête refusée ne coûte presque rien
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // Chemin limité (POST uniquement) → règle de RateLimiter
    private static final Map<String, String> RULES = Map.of(
        "/api/v1/auth/login", RateLimiter.LOGIN,
        "/api/v1/auth/register", RateLimiter.REGISTER,
        "/api/v1/auth/resend-verification", RateLimiter.RESEND);

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !RULES.containsKey(path(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ConsumptionProbe probe = rateLimiter.tryConsume(RULES.get(path(request)), clientIpResolver.resolve(request));
        if (!probe.isConsumed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(RateLimiter.retryAfterSeconds(probe)));
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Trop de tentatives, réessayez plus tard");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
