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

    // Session active de chaque famille (une seule par famille après rotation), la plus récente d'abord
    List<UserSession> findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(User user, OffsetDateTime now);

    // Date de connexion de chaque famille : création de sa première session. Lignes [family_id, min(created_at)]
    @Query("select s.familyId, min(s.createdAt) from UserSession s where s.user = :user and s.familyId in :familyIds group by s.familyId")
    List<Object[]> findFamilyStarts(@Param("user") User user, @Param("familyIds") List<UUID> familyIds);

    // Révocation atomique : renvoie 0 si une autre requête a déjà révoqué cette session
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.id = :id and s.revokedAt is null")
    int revokeIfActive(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    // Réutilisation détectée : toutes les sessions encore actives de la famille (family_id unique dans l'application)
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

    // Révocation par l'utilisateur : filtrée sur son compte, 0 = famille inconnue, d'un autre, déjà révoquée ou expirée
    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.familyId = :familyId and s.user = :user and s.revokedAt is null and s.expiresAt > :now")
    int revokeFamilyOfUser(@Param("familyId") UUID familyId, @Param("user") User user, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.user = :user and s.revokedAt is null")
    int revokeAllOfUser(@Param("user") User user, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.user = :user and s.familyId <> :keptFamilyId and s.revokedAt is null")
    int revokeAllOfUserExceptFamily(@Param("user") User user, @Param("keptFamilyId") UUID keptFamilyId, @Param("now") OffsetDateTime now);
}
