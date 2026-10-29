package app.parkyn.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import app.parkyn.model.User;
import app.parkyn.model.VerificationToken;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    void deleteByUserAndConsumedAtIsNull(User user);

}
