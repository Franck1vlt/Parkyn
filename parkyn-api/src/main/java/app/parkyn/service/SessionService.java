package app.parkyn.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.dto.SessionResponse;
import app.parkyn.model.User;
import app.parkyn.model.UserSession;
import app.parkyn.repository.UserSessionRepository;
import app.parkyn.security.SessionTokens;
import lombok.RequiredArgsConstructor;

/**
 * Sessions actives de l'utilisateur connecté (C4). Une session affichée = une famille = un appareil.
 * Toutes les requêtes sont limitées au compte de l'utilisateur (et filtrées par tenant via @TenantId).
 * L'appareil courant est désigné par le cookie refresh_token, envoyé sur /api/v1/auth.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_NOT_FOUND = "Session introuvable";

    private final UserSessionRepository userSessionRepository;

    @Transactional(readOnly = true)
    public List<SessionResponse> list(User user, String refreshToken) {
        List<UserSession> active = userSessionRepository
            .findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, OffsetDateTime.now());
        if (active.isEmpty()) {
            return List.of();
        }

        Map<UUID, OffsetDateTime> signedInAt = new HashMap<>();
        for (Object[] row : userSessionRepository.findFamilyStarts(user, active.stream().map(UserSession::getFamilyId).toList())) {
            signedInAt.put((UUID) row[0], (OffsetDateTime) row[1]);
        }

        Optional<UUID> current = currentFamily(user, refreshToken);
        return active.stream()
            .map(session -> new SessionResponse(
                session.getFamilyId(),
                session.getUserAgent(),
                session.getIp(),
                signedInAt.get(session.getFamilyId()),
                // Chaque refresh crée une nouvelle session : sa création est la dernière activité de l'appareil
                session.getCreatedAt(),
                current.map(session.getFamilyId()::equals).orElse(false)))
            .toList();
    }

    // Famille inconnue, expirée, d'un autre utilisateur ou d'un autre tenant : même 404, rien n'est révélé
    @Transactional
    public void revoke(User user, UUID familyId) {
        if (userSessionRepository.revokeFamilyOfUser(familyId, user, OffsetDateTime.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, SESSION_NOT_FOUND);
        }
    }

    // « Déconnecter les autres appareils » ; sans cookie, l'appareil courant n'a pas de session : tout est révoqué
    @Transactional
    public void revokeOthers(User user, String refreshToken) {
        Optional<UUID> current = currentFamily(user, refreshToken);
        if (current.isPresent()) {
            userSessionRepository.revokeAllOfUserExceptFamily(user, current.get(), OffsetDateTime.now());
        } else {
            userSessionRepository.revokeAllOfUser(user, OffsetDateTime.now());
        }
    }

    // Logout : seulement l'appareil courant, et seulement si le cookie appartient à l'utilisateur authentifié
    @Transactional
    public void logout(User user, String refreshToken) {
        currentFamily(user, refreshToken)
            .ifPresent(familyId -> userSessionRepository.revokeFamilyOfUser(familyId, user, OffsetDateTime.now()));
    }

    // Désactivation du compte : toutes ses sessions, sans attendre le prochain refresh
    @Transactional
    public void revokeAll(User user) {
        userSessionRepository.revokeAllOfUser(user, OffsetDateTime.now());
    }

    private Optional<UUID> currentFamily(User user, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return userSessionRepository.findByRefreshTokenHash(SessionTokens.hash(refreshToken))
            .filter(session -> session.getUser().getId().equals(user.getId()))
            .map(UserSession::getFamilyId);
    }
}
