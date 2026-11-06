package app.parkyn.security;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import app.parkyn.model.User;
import app.parkyn.service.PermissionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor 
public class JwtAuthFilter extends OncePerRequestFilter{

    private final PermissionService permissionService;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    // Endpoints publics d'authentification : un token joint à la requête est ignoré. Sinon la session prendrait
    // le tenant de ce token : connexion refusée pour un compte d'une autre entreprise, inscription en erreur.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return Arrays.asList(SecurityConfig.PUBLIC_AUTH_PATHS).contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email;
        try {
            email = jwtService.extractUsername(token);
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException e) {
                // Compte supprimé ou email modifié depuis l'émission du token : même 401 qu'un token invalide
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            boolean valid = jwtService.isTokenValid(token, userDetails);
            if (valid && userDetails.isEnabled()) {
                User user = (User) userDetails;
                var permissionAuthorities = permissionService.permissionCodesOf(user.getId()).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, permissionAuthorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                // Pour les logs (vidés par RequestIdFilter) : identifiants seulement, jamais l'email
                MDC.put(RequestIdFilter.TENANT_ID, String.valueOf(user.getTenantId()));
                MDC.put(RequestIdFilter.USER_ID, String.valueOf(user.getId()));
            }
        }

        filterChain.doFilter(request, response);
    }
}
