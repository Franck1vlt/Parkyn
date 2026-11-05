package app.parkyn.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.parkyn.model.User;
import app.parkyn.model.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);
    List<UserSession> findByUserAndRevokedAtIsNull(User user);

    // Révocation atomique : renvoie 0 si une autre requête a déjà révoqué cette session
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.id = :id and s.revokedAt is null")
    int revokeIfActive(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    // Réutilisation détectée : toutes les sessions encore actives de la famille (family_id unique dans l'application)
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);
}
