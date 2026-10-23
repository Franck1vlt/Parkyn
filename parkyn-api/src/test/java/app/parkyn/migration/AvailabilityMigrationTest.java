package app.parkyn.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import app.parkyn.IntegrationTest;

/**
 * V14 sur des données existantes : la base de test est neuve, elle n'a ni véhicule RESERVED
 * ni chevauchement. Les migrations sont donc rejouées jusqu'à la V13 dans un schéma à part,
 * les données y sont insérées, puis la V14 est appliquée.
 */
@IntegrationTest
class AvailabilityMigrationTest {

    private static final String SCHEMA = "v14_check";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void legacyReservedVehiclesBecomeAvailable() {
        migrateTo("13");
        UUID vehicle = insertVehicle("AA-111-AA", "RESERVED");

        migrateTo("14");

        assertEquals("AVAILABLE", statusOf(vehicle));
    }

    // N4 : la migration échoue avec la liste des réservations en cause, sans rien modifier
    @Test
    void existingOverlapsStopTheMigrationWithoutChangingAnything() {
        migrateTo("13");
        UUID vehicle = insertVehicle("AA-111-AA", "RESERVED");
        UUID driver = jdbc.queryForObject(
            "INSERT INTO " + SCHEMA + ".users (first_name, last_name, email) VALUES ('Bob', 'Martin', 'bob@parkyn.app') RETURNING id",
            UUID.class);
        UUID first = insertReservation(vehicle, driver, "1 day", "3 days");
        UUID second = insertReservation(vehicle, driver, "2 days", "4 days");

        var error = assertThrows(FlywayException.class, () -> migrateTo("14"));

        String message = messagesOf(error);
        assertTrue(message.contains("se chevauchent"), message);
        assertTrue(message.contains(first.toString()) && message.contains(second.toString()), message);
        assertEquals("RESERVED", statusOf(vehicle), "La V14 doit être annulée en entier");
    }

    private void migrateTo(String version) {
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(SCHEMA)
            .target(version)
            .load()
            .migrate();
    }

    private UUID insertVehicle(String plate, String status) {
        return jdbc.queryForObject(
            "INSERT INTO " + SCHEMA + ".vehicles (plate_number, brand, model, status) VALUES (?, 'Renault', 'Clio', ?) RETURNING id",
            UUID.class, plate, status);
    }

    private UUID insertReservation(UUID vehicleId, UUID driverId, String start, String end) {
        return jdbc.queryForObject(
            "INSERT INTO " + SCHEMA + ".reservations (vehicle_id, driver_id, start_date, end_date, condition_before, mileage_start) "
                + "VALUES (?, ?, NOW() + CAST(? AS INTERVAL), NOW() + CAST(? AS INTERVAL), 'GOOD', 50000) RETURNING id",
            UUID.class, vehicleId, driverId, start, end);
    }

    private String statusOf(UUID vehicleId) {
        return jdbc.queryForObject("SELECT status FROM " + SCHEMA + ".vehicles WHERE id = ?", String.class, vehicleId);
    }

    // Le message de PostgreSQL est dans une cause de l'exception de Flyway
    private static String messagesOf(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
