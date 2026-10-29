package app.parkyn.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import app.parkyn.model.VerificationToken;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

}
