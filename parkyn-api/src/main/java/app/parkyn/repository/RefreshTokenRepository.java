package app.parkyn.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID>{
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserAndRevokedAtIsNull(User user);

    // Révocation atomique : renvoie 0 si une autre requête a déjà consommé ce token
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.id = :id and r.revokedAt is null")
    int revokeIfActive(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
