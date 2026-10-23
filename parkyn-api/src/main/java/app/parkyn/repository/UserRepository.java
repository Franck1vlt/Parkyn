package app.parkyn.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import app.parkyn.model.User;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    // SQL natif, donc volontairement non filtré par tenant : un email est unique dans toute l'application
    // (la connexion le cherche avant de connaître le tenant). Sans cela, un email pris par une autre
    // entreprise passerait la vérification puis échouerait sur l'index unique (500 au lieu de 409).
    @Query(value = "SELECT EXISTS (SELECT 1 FROM users WHERE lower(email) = lower(:email))", nativeQuery = true)
    boolean existsByEmailInAnyTenant(@Param("email") String email);

    @Query("""
    SELECT DISTINCT u
    FROM User u
    JOIN u.roles r
    WHERE r.name IN :names AND r.tenantId IS NULL
    """)
    List<User> findByNamesAndNoTenant(@Param("names") List<String> names);

    @Query("""
    SELECT p.code FROM User u
    JOIN u.roles r
    JOIN r.permissions p
    WHERE u.id = :userId
    """)
    Set<String> findPermissionCodesByUserId(@Param("userId") UUID userId);

}
