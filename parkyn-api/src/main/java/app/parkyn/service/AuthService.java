package app.parkyn.service;

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
import app.parkyn.dto.RefreshRequest;
import app.parkyn.dto.RegisterRequest;
import app.parkyn.email.EmailRequest;
import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;
import app.parkyn.repository.RefreshTokenRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.JwtService;
import app.parkyn.tenant.Tenants;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RoleAssignmentService roleAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final VerificationTokenService verificationTokenService;

    @Value("${jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    @Value("${jwt.expiration-ms}")
    private long accessExpirationMs;

    @Value("${parkyn.frontend-url}")
    private String frontendUrl;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userRepository.findByEmail(User.normalizeEmail(request.email())).orElseThrow();

        String accessToken = jwtService.generateToken(user);
        String rawRefresh = UUID.randomUUID().toString();

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(rawRefresh);
        rt.setExpiresAt(OffsetDateTime.now().plusDays(refreshExpirationDays));
        refreshTokenRepository.save(rt);

        OffsetDateTime accessExpiredAt = OffsetDateTime.now().plusNanos(accessExpirationMs * 1_000_000L);
        return new LoginResponse(accessToken, accessExpiredAt, rawRefresh, rt.getExpiresAt());
    }

    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        // Un compte désactivé ou en attente de vérification n'obtient plus de nouvel access token
        if (!stored.getUser().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        stored.setRevokedAt(OffsetDateTime.now());

        String newAccess = jwtService.generateToken(stored.getUser());
        String newRaw = UUID.randomUUID().toString();

        RefreshToken newRt = new RefreshToken();
        newRt.setUser(stored.getUser());
        newRt.setToken(newRaw);
        newRt.setExpiresAt(OffsetDateTime.now().plusDays(refreshExpirationDays));
        refreshTokenRepository.save(newRt);

        OffsetDateTime accessExpiredAt = OffsetDateTime.now().plusNanos(accessExpirationMs * 1_000_000L);
        return new LoginResponse(newAccess, accessExpiredAt, newRaw, newRt.getExpiresAt());
    }
    
    @Transactional
    public void logout(User user) {
        refreshTokenRepository.findByUserAndRevokedAtIsNull(user)
            .forEach(rt -> rt.setRevokedAt(OffsetDateTime.now()));
    }

    @Transactional
    public void createAccount(RegisterRequest request) {
        String hashedPassword = passwordEncoder.encode(request.password());
        String normalizedEmail = User.normalizeEmail(request.email());

        if (userRepository.existsByEmailInAnyTenant(normalizedEmail)) {
            User existing = userRepository.findByEmail(normalizedEmail).orElseThrow();
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

        String token = verificationTokenService.issue(user);
        eventPublisher.publishEvent(new EmailRequest(
            user.getEmail(), "Vérifiez votre adresse email", "verify-email",
            Map.of("name", user.getFirstName(), "link", frontendUrl + "/verify-email?token=" + token)));
    }

    public MeResponse me(User user) {
        return MeResponse.from(user);
    }

}
