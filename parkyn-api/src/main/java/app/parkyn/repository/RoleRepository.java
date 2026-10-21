package app.parkyn.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import app.parkyn.model.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNameAndTenantIdIsNull(String name);

}
