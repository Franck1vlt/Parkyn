package app.parkyn.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import app.parkyn.model.RefreshToken;
import app.parkyn.model.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID>{
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserAndRevokedAtIsNull(User user);
}
