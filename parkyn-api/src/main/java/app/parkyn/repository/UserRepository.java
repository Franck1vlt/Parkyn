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
    Optional<User> findByEmail(String email);

    List<User> findByRoleIn(List<User.Role> roles);

    @Query("""
    SELECT p.code FROM User u
    JOIN u.roles r
    JOIN r.permissions p
    WHERE u.id = :userId
    """)
    Set<String> findPermissionCodesByUserId(@Param("userId") UUID userId);

}
