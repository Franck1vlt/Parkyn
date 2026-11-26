package app.parkyn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.JwtService;

/**
 * Suppression d'un véhicule : refusée (409) tant qu'une réservation ni retournée ni annulée existe.
 * Les véhicules et réservations sont insérés en SQL, sans passer par le code testé.
 */
@AutoConfigureMockMvc
@IntegrationTest
class VehicleDeletionTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final OffsetDateTime T0 = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    private static final int MILEAGE = 50000;
    // Phrase écrite en littéral : le test ne dépend pas de la constante de production
    private static final String CONFLICT_MESSAGE =
        "Ce véhicule a des réservations à venir ou en cours : annulez-les avant de le supprimer";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User adminA;
    private User agentA;
    private User driverA;
    private User adminB;
    private User agentB;
    private User driverB;
    private UUID vehicleA;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO tenants (id, name) VALUES (?, ?), (?, ?)",
            TENANT_A, "Entreprise A", TENANT_B, "Entreprise B");
        TestUsers testUsers = new TestUsers(userRepository, jdbc, passwordEncoder);
        TestUsers testUsersA = testUsers.inTenant(TENANT_A);
        TestUsers testUsersB = testUsers.inTenant(TENANT_B);

        adminA = testUsersA.createWithoutPassword("admin@a.test", User.Role.ADMIN);
        agentA = testUsersA.createWithoutPassword("agent@a.test", User.Role.AGENT);
        driverA = testUsersA.createWithoutPassword("driver@a.test", User.Role.DRIVER);
        adminB = testUsersB.createWithoutPassword("admin@b.test", User.Role.ADMIN);
        agentB = testUsersB.createWithoutPassword("agent@b.test", User.Role.AGENT);
        driverB = testUsersB.createWithoutPassword("driver@b.test", User.Role.DRIVER);
        vehicleA = insertVehicle(TENANT_A, "AA-111-AA");
    }

    // --- Réservations qui bloquent la suppression ---

    @Test
    void vehicleWithUpcomingReservationCannotBeDeleted() throws Exception {
        UUID reservation = insertA(T0.plusDays(5), T0.plusDays(6), null, null, null);

        deleteAs(adminA, vehicleA)
            .andExpect(status().isConflict())
            .andExpect(content().string(CONFLICT_MESSAGE));

        assertNull(deletedAtOf(vehicleA));
        assertEquals(1, countReservationsOf(vehicleA));
        assertEquals(1, countById(reservation));
    }

    @Test
    void vehicleWithAwaitingHandoverReservationCannotBeDeleted() throws Exception {
        // Début passé, ni remise ni annulation
        insertA(T0.minusHours(2), T0.plusHours(2), null, null, null);

        deleteAs(adminA, vehicleA)
            .andExpect(status().isConflict())
            .andExpect(content().string(CONFLICT_MESSAGE));

        assertNull(deletedAtOf(vehicleA));
    }

    @Test
    void vehicleWithInProgressReservationCannotBeDeleted() throws Exception {
        // Remise faite, fin dépassée (en retard), non retournée
        insertA(T0.minusDays(3), T0.minusDays(1), T0.minusDays(3), null, null);

        deleteAs(adminA, vehicleA)
            .andExpect(status().isConflict())
            .andExpect(content().string(CONFLICT_MESSAGE));

        assertNull(deletedAtOf(vehicleA));
    }

    @Test
    void vehicleWithOnHoldReservationCannotBeDeleted() throws Exception {
        // La première, remise et non retournée, retient le véhicule ; la suivante attend sans chevauchement
        insertA(T0.minusDays(3), T0.minusDays(2), T0.minusDays(3), null, null);
        insertA(T0.minusDays(1), T0.plusDays(1), null, null, null);

        deleteAs(adminA, vehicleA)
            .andExpect(status().isConflict())
            .andExpect(content().string(CONFLICT_MESSAGE));

        assertNull(deletedAtOf(vehicleA));
        assertEquals(2, countReservationsOf(vehicleA));
    }

    @Test
    void upcomingReservationBlocksEvenWithReturnedHistory() throws Exception {
        insertA(T0.minusDays(5), T0.minusDays(4), T0.minusDays(5), T0.minusDays(4), null);
        insertA(T0.plusDays(5), T0.plusDays(6), null, null, null);

        deleteAs(adminA, vehicleA)
            .andExpect(status().isConflict())
            .andExpect(content().string(CONFLICT_MESSAGE));

        assertNull(deletedAtOf(vehicleA));
        assertEquals(2, countReservationsOf(vehicleA));
    }

    // --- Réservations qui ne bloquent pas ---

    @Test
    void vehicleWithOnlyReturnedReservationsCanBeDeleted() throws Exception {
        insertA(T0.minusDays(10), T0.minusDays(9), T0.minusDays(10), T0.minusDays(9), null);
        // Retournée en avance : sa fin prévue est dans le futur
        insertA(T0.minusDays(3), T0.plusDays(2), T0.minusDays(3), T0.minusDays(1), null);

        deleteAs(adminA, vehicleA).andExpect(status().isNoContent());

        assertNotNull(deletedAtOf(vehicleA));
        assertEquals(2, countReservationsOf(vehicleA));
    }

    @Test
    void vehicleWithOnlyCancelledReservationsCanBeDeleted() throws Exception {
        // Annulée à venir, et annulée « en cours » (remise faite, non retournée) : seul cancelled_at les distingue
        insertA(T0.plusDays(5), T0.plusDays(6), null, null, T0.minusHours(1));
        insertA(T0.minusDays(3), T0.minusDays(1), T0.minusDays(3), null, T0.minusDays(2));

        deleteAs(adminA, vehicleA).andExpect(status().isNoContent());

        assertNotNull(deletedAtOf(vehicleA));
        assertEquals(2, countReservationsOf(vehicleA));
    }

    // --- Tenant ---

    @Test
    void otherTenantVehicleWithReservationAnswersNotFound() throws Exception {
        UUID reservation = insertA(T0.plusDays(5), T0.plusDays(6), null, null, null);

        MvcResult unknown = deleteAs(adminB, UUID.randomUUID()).andExpect(status().isNotFound()).andReturn();
        MvcResult otherTenant = deleteAs(adminB, vehicleA).andExpect(status().isNotFound()).andReturn();

        assertEquals(unknown.getResponse().getContentAsString(), otherTenant.getResponse().getContentAsString());
        assertNull(deletedAtOf(vehicleA));
        assertEquals(1, countById(reservation));
        assertNull(jdbc.queryForObject("SELECT cancelled_at FROM reservations WHERE id = ?",
            OffsetDateTime.class, reservation));
    }

    @Test
    void reservationOfOtherTenantDoesNotBlockDeletion() throws Exception {
        // Ligne incohérente du tenant B sur le véhicule de A (la base ne l'interdit pas : pas de clé composite)
        UUID reservationB = insertReservation(TENANT_B, vehicleA, driverB, agentB,
            T0.plusDays(5), T0.plusDays(6), null, null, null);

        deleteAs(adminA, vehicleA).andExpect(status().isNoContent());

        assertNotNull(deletedAtOf(vehicleA));
        assertEquals(1, countReservationsOf(vehicleA));
        // La réservation de B reste intacte : ni annulée, ni retournée, tenant inchangé
        assertEquals(1, countById(reservationB));
        assertEquals(TENANT_B, tenantOfReservation(reservationB));
        assertNull(cancelledAtOfReservation(reservationB));
        assertNull(returnedAtOfReservation(reservationB));
    }

    @Test
    void ownReservationStillBlocksWhenOtherTenantReservationExists() throws Exception {
        // Dates sans chevauchement : reservations_no_overlap ne regarde que vehicle_id
        insertReservation(TENANT_B, vehicleA, driverB, agentB, T0.plusDays(5), T0.plusDays(6), null, null, null);
        insertA(T0.plusDays(8), T0.plusDays(9), null, null, null);

        deleteAs(adminA, vehicleA)
            .andExpect(status().isConflict())
            .andExpect(content().string(CONFLICT_MESSAGE));

        assertNull(deletedAtOf(vehicleA));
        assertEquals(2, countReservationsOf(vehicleA));
    }

    @Test
    void reservationOfOtherTenantDoesNotBlockDeletionInReverse() throws Exception {
        // Sens inverse : véhicule de B, réservation portant le tenant A
        UUID vehicleB = insertVehicle(TENANT_B, "BB-222-BB");
        UUID reservationA = insertReservation(TENANT_A, vehicleB, driverA, agentA,
            T0.plusDays(5), T0.plusDays(6), null, null, null);

        deleteAs(adminB, vehicleB).andExpect(status().isNoContent());

        assertNotNull(deletedAtOf(vehicleB));
        assertEquals(1, countById(reservationA));
        assertEquals(TENANT_A, tenantOfReservation(reservationA));
        assertNull(cancelledAtOfReservation(reservationA));
        assertNull(returnedAtOfReservation(reservationA));
    }

    // --- Permission ---

    @Test
    void agentWithoutDeletePermissionGets403BeforeBusinessRule() throws Exception {
        insertA(T0.plusDays(5), T0.plusDays(6), null, null, null);

        deleteAs(agentA, vehicleA).andExpect(status().isForbidden());

        assertNull(deletedAtOf(vehicleA));
        assertEquals(1, countReservationsOf(vehicleA));
    }

    // --- Outils ---

    private org.springframework.test.web.servlet.ResultActions deleteAs(User actor, UUID vehicleId) throws Exception {
        return mockMvc.perform(delete("/api/v1/vehicles/" + vehicleId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(actor)));
    }

    private UUID insertVehicle(UUID tenantId, String plate) {
        return jdbc.queryForObject(
            "INSERT INTO vehicles (tenant_id, plate_number, brand, model, mileage_km, condition, status) "
                + "VALUES (?, ?, 'Toyota', 'Yaris', ?, 'GOOD', 'AVAILABLE') RETURNING id",
            UUID.class, tenantId, plate, MILEAGE);
    }

    // Réservation du tenant A sur le véhicule de A
    private UUID insertA(OffsetDateTime start, OffsetDateTime end, OffsetDateTime handedOverAt,
                         OffsetDateTime returnedAt, OffsetDateTime cancelledAt) {
        return insertReservation(TENANT_A, vehicleA, driverA, agentA, start, end, handedOverAt, returnedAt, cancelledAt);
    }

    private UUID insertReservation(UUID tenantId, UUID vehicleId, User driver, User agent,
                                   OffsetDateTime start, OffsetDateTime end,
                                   OffsetDateTime handedOverAt, OffsetDateTime returnedAt, OffsetDateTime cancelledAt) {
        return jdbc.queryForObject(
            "INSERT INTO reservations (tenant_id, vehicle_id, driver_id, agent_id, start_date, end_date, "
                + "condition_before, mileage_start, handed_over_at, returned_at, cancelled_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'GOOD', ?, ?, ?, ?) RETURNING id",
            UUID.class, tenantId, vehicleId, driver.getId(), agent.getId(), start, end, MILEAGE,
            handedOverAt, returnedAt, cancelledAt);
    }

    private OffsetDateTime deletedAtOf(UUID vehicleId) {
        return jdbc.queryForObject("SELECT deleted_at FROM vehicles WHERE id = ?", OffsetDateTime.class, vehicleId);
    }

    private int countReservationsOf(UUID vehicleId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE vehicle_id = ?", Integer.class, vehicleId);
    }

    private UUID tenantOfReservation(UUID reservationId) {
        return jdbc.queryForObject("SELECT tenant_id FROM reservations WHERE id = ?", UUID.class, reservationId);
    }

    private OffsetDateTime cancelledAtOfReservation(UUID reservationId) {
        return jdbc.queryForObject("SELECT cancelled_at FROM reservations WHERE id = ?",
            OffsetDateTime.class, reservationId);
    }

    private OffsetDateTime returnedAtOfReservation(UUID reservationId) {
        return jdbc.queryForObject("SELECT returned_at FROM reservations WHERE id = ?",
            OffsetDateTime.class, reservationId);
    }

    private int countById(UUID reservationId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE id = ?", Integer.class, reservationId);
    }
}
