package app.parkyn.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.MeResponse;
import app.parkyn.dto.RegisterRequest;
import app.parkyn.email.EmailRequest;
import app.parkyn.exception.SessionRejectedException;
import app.parkyn.model.User;
import app.parkyn.model.UserSession;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.UserSessionRepository;
import app.parkyn.security.JwtService;
import app.parkyn.security.RateLimiter;
import app.parkyn.security.SessionTokens;
import app.parkyn.tenant.Tenants;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RoleAssignmentService roleAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final VerificationTokenService verificationTokenService;
    private final RateLimiter rateLimiter;

    // Même message pour tous les refus du refresh : le client ne doit pas savoir si le token a existé
    private static final String SESSION_EXPIRED = "Session expirée, veuillez vous reconnecter";

    @Value("${jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    @Value("${jwt.expiration-ms}")
    private long accessExpirationMs;

    @Value("${parkyn.session.reuse-grace}")
    private Duration reuseGrace;

    @Value("${parkyn.frontend-url}")
    private String frontendUrl;

    @Transactional
    public AuthTokens login(LoginRequest request, ClientInfo client) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userRepository.findByEmail(User.normalizeEmail(request.email())).orElseThrow();

        // Chaque login ouvre une nouvelle famille de sessions
        return openSession(user, UUID.randomUUID(), client);
    }

    // SessionRejectedException : refus après révocation, la transaction est validée (pas de rollback)
    @Transactional(noRollbackFor = SessionRejectedException.class)
    public AuthTokens refresh(String refreshToken, ClientInfo client) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
        }

        UserSession stored = userSessionRepository.findByRefreshTokenHash(SessionTokens.hash(refreshToken))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED));

        // Token déjà consommé. Juste après la rotation : course légitime (deux onglets), refus simple.
        // Au-delà de la grâce : token probablement volé, toute la famille est révoquée (voleur et utilisateur)
        if (stored.getRevokedAt() != null) {
            if (stored.getRevokedAt().isBefore(OffsetDateTime.now().minus(reuseGrace))) {
                userSessionRepository.revokeFamily(stored.getFamilyId(), OffsetDateTime.now());
                throw new SessionRejectedException(SESSION_EXPIRED);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
        }

        // Session expirée, ou compte désactivé ou en attente de vérification : session révoquée, puis refus
        User user = stored.getUser();
        if (stored.getExpiresAt().isBefore(OffsetDateTime.now()) || !user.isEnabled()) {
            userSessionRepository.revokeIfActive(stored.getId(), OffsetDateTime.now());
            throw new SessionRejectedException(SESSION_EXPIRED);
        }

        // Usage unique : seule la requête qui révoque la session obtient la suivante.
        // UPDATE en masse : l'entité stored garde revokedAt = null en mémoire. Ne plus la modifier
        // ensuite, sinon Hibernate réécrirait revoked_at = null au commit et annulerait la révocation.
        if (userSessionRepository.revokeIfActive(stored.getId(), OffsetDateTime.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
        }

        return openSession(user, stored.getFamilyId(), client);
    }

    // Déconnecte l'utilisateur de tous ses appareils (choix « cette session seulement » : C4)
    @Transactional
    public void logout(User user) {
        userSessionRepository.findByUserAndRevokedAtIsNull(user)
            .forEach(session -> session.setRevokedAt(OffsetDateTime.now()));
    }

    // Login et refresh hors connexion (contexte ROOT) : le tenant de la session est fixé ici
    private AuthTokens openSession(User user, UUID familyId, ClientInfo client) {
        String rawRefresh = SessionTokens.generate();

        UserSession session = new UserSession();
        session.setTenantId(user.getTenantId());
        session.setUser(user);
        session.setRefreshTokenHash(SessionTokens.hash(rawRefresh));
        session.setFamilyId(familyId);
        session.setUserAgent(truncate(client.userAgent(), UserSession.USER_AGENT_MAX_LENGTH));
        session.setIp(client.ip());
        session.setExpiresAt(OffsetDateTime.now().plusDays(refreshExpirationDays));
        userSessionRepository.save(session);

        String accessToken = jwtService.generateToken(user);
        OffsetDateTime accessExpiredAt = OffsetDateTime.now().plusNanos(accessExpirationMs * 1_000_000L);
        LoginResponse body = new LoginResponse(accessToken, accessExpiredAt, session.getExpiresAt());
        return new AuthTokens(body, rawRefresh);
    }

    @Transactional
    public void createAccount(RegisterRequest request) {
        String hashedPassword = passwordEncoder.encode(request.password());
        String normalizedEmail = User.normalizeEmail(request.email());

        if (userRepository.existsByEmailInAnyTenant(normalizedEmail)) {
            User existing = userRepository.findByEmail(normalizedEmail).orElseThrow();
            if (existing.getStatus() == User.Status.PENDING_EMAIL_VERIFICATION) {
                sendVerificationEmail(existing);
                return;
            }

            if (!rateLimiter.tryConsumeEmail(existing.getEmail())) {
                return;
            }

            eventPublisher.publishEvent(new EmailRequest(
                existing.getEmail(), "Votre compte Parkyn", "account-exists",
                Map.of("name", existing.getFirstName(), "link", frontendUrl + "/login")));
            return;
        }

        User user = new User();
        user.setFirstName(request.firstname());
        user.setLastName(request.lastname());
        user.setEmail(request.email());
        user.setHashedPwd(hashedPassword);
        // Inscription hors connexion : aucun tenant courant, il est fixé ici (invitations par entreprise au lot 4)
        user.setTenantId(Tenants.DEFAULT_ID);
        roleAssignmentService.assign(user, User.Role.DRIVER);
        user.setStatus(User.Status.PENDING_EMAIL_VERIFICATION);
        userRepository.saveAndFlush(user);

        sendVerificationEmail(user);
    }

    @Transactional 
    public void resendVerification(String email) {
        String normalizedEmail = User.normalizeEmail(email);
        if (!userRepository.existsByEmailInAnyTenant(normalizedEmail)) {
            return;
        }

        userRepository.findByEmail(normalizedEmail)
            .filter(user -> user.getStatus() == User.Status.PENDING_EMAIL_VERIFICATION)
            .ifPresent(this::sendVerificationEmail);
    }

    private void sendVerificationEmail(User user) {
        if (!rateLimiter.tryConsumeEmail(user.getEmail())) {
            return;
        }

        String token = verificationTokenService.issue(user);
        eventPublisher.publishEvent(new EmailRequest(
            user.getEmail(), "Vérifiez votre adresse email", "verify-email",
            Map.of("name", user.getFirstName(), "link", frontendUrl + "/verify-email?token=" + token)));
    }

    public MeResponse me(User user) {
        return MeResponse.from(user);
    }

    // En-tête choisi par le client : tronqué à la taille de la colonne plutôt qu'un 500 au login
    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

}
