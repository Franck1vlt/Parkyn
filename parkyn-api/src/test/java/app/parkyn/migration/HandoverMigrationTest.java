package app.parkyn.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import app.parkyn.IntegrationTest;

/**
 * V17 sur des données existantes (décision H5) : avant la V17, la remise des clés n'était pas
 * enregistrée. Une réservation déjà commencée ou retournée est considérée comme remise à son début ;
 * sinon, les véhicules actuellement dehors apparaîtraient disponibles.
 */
@IntegrationTest
class HandoverMigrationTest {

    private static final String SCHEMA = "v17_check";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    private UUID vehicle;
    private UUID driver;

    @BeforeEach
    void migrateBefore() {
        migrateTo("16");
        vehicle = jdbc.queryForObject(
            "INSERT INTO " + SCHEMA + ".vehicles (plate_number, brand, model) VALUES ('AA-111-AA', 'Renault', 'Clio') RETURNING id",
            UUID.class);
        driver = jdbc.queryForObject(
            "INSERT INTO " + SCHEMA + ".users (first_name, last_name, email) VALUES ('Bob', 'Martin', 'bob@parkyn.app') RETURNING id",
            UUID.class);
    }

    @AfterEach
    void dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void startedOrReturnedReservationsAreConsideredHandedOver() {
        UUID ongoing = insertReservation("-1 day", "1 day");
        UUID returned = insertReservation("-5 days", "-4 days");
        jdbc.update("UPDATE " + SCHEMA + ".reservations SET returned_at = end_date WHERE id = ?", returned);

        migrateTo("17");

        assertEquals(startOf(ongoing), handedOverAt(ongoing));
        assertEquals(startOf(returned), handedOverAt(returned));
    }

    @Test
    void upcomingAndCancelledReservationsAreNotHandedOver() {
        UUID upcoming = insertReservation("2 days", "3 days");
        UUID cancelled = insertReservation("-3 days", "-2 days");
        jdbc.update("UPDATE " + SCHEMA + ".reservations SET cancelled_at = NOW() - INTERVAL '4 days' WHERE id = ?", cancelled);

        migrateTo("17");

        assertNull(handedOverAt(upcoming));
        assertNull(handedOverAt(cancelled));
    }

    // Avant la V17, une réservation commencée derrière une autre pas retournée attendait le véhicule :
    // seule la première est considérée comme remise
    @Test
    void reservationOnHoldBeforeMigrationIsNotHandedOver() {
        UUID late = insertReservation("-5 hours", "-3 hours");
        UUID waiting = insertReservation("-1 hour", "2 hours");

        migrateTo("17");

        assertEquals(startOf(late), handedOverAt(late));
        assertNull(handedOverAt(waiting));
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).target(version).load().migrate();
    }

    // Période relative à maintenant (ex. "-1 day")
    private UUID insertReservation(String start, String end) {
        return jdbc.queryForObject(
            "INSERT INTO " + SCHEMA + ".reservations (vehicle_id, driver_id, start_date, end_date, condition_before, mileage_start) "
                + "VALUES (?, ?, NOW() + CAST(? AS INTERVAL), NOW() + CAST(? AS INTERVAL), 'GOOD', 50000) RETURNING id",
            UUID.class, vehicle, driver, start, end);
    }

    private OffsetDateTime startOf(UUID reservation) {
        return jdbc.queryForObject("SELECT start_date FROM " + SCHEMA + ".reservations WHERE id = ?", OffsetDateTime.class, reservation);
    }

    private OffsetDateTime handedOverAt(UUID reservation) {
        return jdbc.queryForObject("SELECT handed_over_at FROM " + SCHEMA + ".reservations WHERE id = ?", OffsetDateTime.class, reservation);
    }
}
