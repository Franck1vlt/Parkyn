package app.parkyn.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import app.parkyn.IntegrationTest;

@IntegrationTest
class FlywayMigrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    // Insère un utilisateur et renvoie son id (seules les colonnes sans valeur par défaut sont fournies)
    private UUID insertUser(String email, String status) {
        return jdbc.queryForObject(
            "INSERT INTO users (first_name, last_name, email, status) VALUES (?, ?, ?, ?) RETURNING id",
            UUID.class,
            "Test", "User", email, status);
    }

    private UUID insertUser(String email) {
        return insertUser(email, "ACTIVE");
    }

    @Test
    void allMigrationsAreApplied() {
        assertFalse(Arrays.stream(flyway.info().all())
            .anyMatch(m -> m.getState() == MigrationState.PENDING
                || m.getState() == MigrationState.FAILED));
    }

    @Test
    void referenceDataIsSeeded() {
        assertEquals(23, jdbc.queryForObject("SELECT COUNT(*) FROM permissions", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE tenant_id IS NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Integer.class));
    }

    @Test
    void systemRolesHaveExpectedPermissionCounts() {
        String sql = "SELECT r.name, COUNT(rp.permission_id) AS permission_count "
            + "FROM roles r "
            + "JOIN role_permissions rp ON r.id = rp.role_id "
            + "WHERE r.tenant_id IS NULL "
            + "GROUP BY r.name";
        Map<String, Integer> permissionCounts = new HashMap<>();
        jdbc.queryForList(sql).forEach(row -> permissionCounts.put(
            (String) row.get("name"), ((Number) row.get("permission_count")).intValue()));

        assertEquals(18, permissionCounts.get("ADMIN"));
        assertEquals(14, permissionCounts.get("AGENT"));
        assertEquals(7, permissionCounts.get("DRIVER"));
    }

    @Test
    void invalidUserStatusIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () ->
            insertUser("test@parkyn.app", "FOO"));
    }

    @Test
    void emailUniquenessIgnoresCase() {
        insertUser("Jean@parkyn.app");

        assertThrows(DataIntegrityViolationException.class, () ->
            insertUser("jean@parkyn.app"));
    }

    @Test
    void duplicateSystemRoleIsRejected() {
        // ADMIN existe déjà (V6). L'insertion échoue, donc rien ne reste dans roles.
        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("INSERT INTO roles (tenant_id, name) VALUES (NULL, 'ADMIN')"));
    }

    @Test
    void expiredSessionIsRejected() {
        UUID userId = insertUser("test@parkyn.app");

        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update(
                "INSERT INTO user_sessions (user_id, refresh_token_hash, family_id, expires_at) "
                    + "VALUES (?, ?, ?, NOW() - INTERVAL '1 day')",
                userId, "hash-de-test", UUID.randomUUID()));
    }

    @Test
    void deletingUserDeletesItsRoles() {
        UUID userId = insertUser("bob@parkyn.app");
        UUID driverRoleId = jdbc.queryForObject(
            "SELECT id FROM roles WHERE name = 'DRIVER' AND tenant_id IS NULL", UUID.class);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, driverRoleId);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_roles WHERE user_id = ?", Integer.class, userId));
    }

    @Test
    void deletingDriverWithReservationIsRefused() {
        UUID driverId = insertUser("bob@parkyn.app");

        UUID vehicleId = jdbc.queryForObject(
            "INSERT INTO vehicles (plate_number, brand, model) VALUES (?, ?, ?) RETURNING id",
            UUID.class,
            "AB-123-CD", "Renault", "Clio");

        jdbc.update(
            "INSERT INTO reservations (vehicle_id, driver_id, start_date, end_date, condition_before, mileage_start) "
                + "VALUES (?, ?, NOW() + INTERVAL '1 day', NOW() + INTERVAL '2 days', ?, ?)",
            vehicleId, driverId, "GOOD", 50000);

        // driver_id n'a pas de CASCADE : la suppression du conducteur est refusée pour garder l'historique
        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("DELETE FROM users WHERE id = ?", driverId));

        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM reservations WHERE driver_id = ?", Integer.class, driverId));
    }

    @Test
    void usersTableHasNoLegacyRoleColumn() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'users' AND column_name = 'role'",
            Integer.class);

        assertEquals(0, count, "La colonne users.role doit avoir été supprimée (Contract)");
    }
}

        