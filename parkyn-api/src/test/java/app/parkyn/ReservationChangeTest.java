package app.parkyn;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.JwtService;
import app.parkyn.service.NotificationService;
import app.parkyn.tenant.Tenants;
import tools.jackson.databind.ObjectMapper;

/**
 * Modification et annulation d'une réservation (décisions R2 à R6) :
 * - DRIVER (own) : sa réservation uniquement, et seulement avant son début ;
 * - AGENT, ADMIN (all) : toutes les réservations du tenant ; annulation avant le début,
 *   modification tant qu'elle n'est pas retournée, sans toucher au début une fois commencée ;
 * - champs modifiables : startDate, endDate, notesBefore ;
 * - annulation douce : la réservation reste dans l'historique (CANCELLED), ses notifications sont
 *   remplacées par une seule notification d'annulation aux agents et administrateurs.
 */
@AutoConfigureMockMvc
@IntegrationTest
class ReservationChangeTest {

    private static final String SLOT_TAKEN =
        "Véhicule déjà réservé sur ce créneau : choisissez un autre véhicule ou une autre plage horaire";
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    private static final OffsetDateTime DAY2 = NOW.plusDays(2).truncatedTo(ChronoUnit.DAYS);
    private static final int MILEAGE = 50000;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired NotificationService notificationService;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;

    private User driverA;
    private User driverB;
    private User agent;
    private User admin;
    private UUID car;
    private UUID otherCar;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // Réservation de driverA dans deux jours (10 h - 12 h), et une autre déjà commencée
    private UUID upcomingA;
    private UUID startedA;
    private UUID upcomingB;

    @BeforeEach
    void setUp() {
        TestUsers testUsers = new TestUsers(userRepository, jdbc, passwordEncoder);
        driverA = testUsers.createWithoutPassword("driverA@parkyn.app", User.Role.DRIVER);
        driverB = testUsers.createWithoutPassword("driverB@parkyn.app", User.Role.DRIVER);
        agent = testUsers.createWithoutPassword("agent@parkyn.app", User.Role.AGENT);
        admin = testUsers.createWithoutPassword("admin@parkyn.app", User.Role.ADMIN);
        car = insertVehicle("AA-111-AA");
        otherCar = insertVehicle("BB-222-BB");

        upcomingA = insertReservation(driverA, car, DAY2.plusHours(10), DAY2.plusHours(12));
        startedA = insertReservation(driverA, otherCar, NOW.minusHours(1), NOW.plusHours(2));
        upcomingB = insertReservation(driverB, car, DAY2.plusHours(14), DAY2.plusHours(16));
    }

    // --- DRIVER : sa réservation, avant le début ---

    @Test
    void driverUpdatesOwnReservationBeforeStart() throws Exception {
        update(driverA, upcomingA, Map.of(
                "startDate", DAY2.plusHours(8).toString(),
                "endDate", DAY2.plusHours(9).toString(),
                "notesBefore", "Siège bébé"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UPCOMING"))
            .andExpect(jsonPath("$.notesBefore").value("Siège bébé"));

        assertEquals(DAY2.plusHours(8).toInstant(), column(upcomingA, "start_date", OffsetDateTime.class).toInstant());
        assertEquals(DAY2.plusHours(9).toInstant(), column(upcomingA, "end_date", OffsetDateTime.class).toInstant());
        assertEquals(driverA.getId(), column(upcomingA, "updated_by", UUID.class));
    }

    @Test
    void driverCannotUpdateAfterStart() throws Exception {
        update(driverA, startedA, Map.of("endDate", NOW.plusHours(3).toString()))
            .andExpect(status().isConflict());

        assertEquals(NOW.plusHours(2).toInstant(), column(startedA, "end_date", OffsetDateTime.class).toInstant());
    }

    // Hors portée : même réponse qu'un id inexistant
    @Test
    void driverCannotTouchSomeoneElsesReservation() throws Exception {
        update(driverA, upcomingB, Map.of("notesBefore", "Pirate")).andExpect(status().isNotFound());
        cancel(driverA, upcomingB).andExpect(status().isNotFound());
        update(driverA, UUID.randomUUID(), Map.of("notesBefore", "Pirate")).andExpect(status().isNotFound());

        assertNull(column(upcomingB, "notes_before", String.class));
        assertNull(column(upcomingB, "cancelled_at", OffsetDateTime.class));
    }

    @Test
    void driverCancelsOwnReservationBeforeStart() throws Exception {
        cancel(driverA, upcomingA)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        assertNotNull(column(upcomingA, "cancelled_at", OffsetDateTime.class));
        assertEquals(driverA.getId(), column(upcomingA, "cancelled_by", UUID.class));
    }

    @Test
    void driverCannotCancelAfterStart() throws Exception {
        cancel(driverA, startedA).andExpect(status().isConflict());

        assertNull(column(startedA, "cancelled_at", OffsetDateTime.class));
    }

    // --- AGENT : toutes les réservations du tenant ---

    @Test
    void agentUpdatesADriversReservation() throws Exception {
        update(agent, upcomingA, Map.of("notesBefore", "Clés à l'accueil"))
            .andExpect(status().isOk());

        assertEquals("Clés à l'accueil", column(upcomingA, "notes_before", String.class));
        assertEquals(agent.getId(), column(upcomingA, "updated_by", UUID.class));
    }

    // L'agent prolonge une location en cours (cas du retard géré par l'agent)
    @Test
    void agentExtendsAStartedReservation() throws Exception {
        update(agent, startedA, Map.of("endDate", NOW.plusHours(5).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        assertEquals(NOW.plusHours(5).toInstant(), column(startedA, "end_date", OffsetDateTime.class).toInstant());
    }

    @Test
    void startOfAStartedReservationCannotMove() throws Exception {
        update(agent, startedA, Map.of("startDate", NOW.plusHours(1).toString()))
            .andExpect(status().isConflict());

        assertEquals(NOW.minusHours(1).toInstant(), column(startedA, "start_date", OffsetDateTime.class).toInstant());
    }

    // Après le début, ce n'est plus une annulation mais un retour
    @Test
    void agentCannotCancelAStartedReservation() throws Exception {
        cancel(agent, startedA).andExpect(status().isConflict());
    }

    @Test
    void adminCancelsAnyReservationBeforeStart() throws Exception {
        cancel(admin, upcomingB).andExpect(status().isOk());

        assertEquals(admin.getId(), column(upcomingB, "cancelled_by", UUID.class));
    }

    // --- Validation des nouvelles dates ---

    // La réservation ne se gêne pas elle-même : garder son créneau, ou le décaler sur lui-même
    @Test
    void reservationDoesNotConflictWithItself() throws Exception {
        update(driverA, upcomingA, Map.of(
                "startDate", DAY2.plusHours(11).toString(),
                "endDate", DAY2.plusHours(13).toString()))
            .andExpect(status().isOk());
    }

    @Test
    void newSlotOverlappingAnotherReservationIsRefused() throws Exception {
        update(driverA, upcomingA, Map.of("endDate", DAY2.plusHours(15).toString()))
            .andExpect(status().isConflict())
            .andExpect(result -> assertEquals(SLOT_TAKEN, result.getResponse().getContentAsString()));

        assertEquals(DAY2.plusHours(12).toInstant(), column(upcomingA, "end_date", OffsetDateTime.class).toInstant());
    }

    @Test
    void invalidDatesAreRefused() throws Exception {
        update(driverA, upcomingA, Map.of("endDate", DAY2.plusHours(9).toString()))
            .andExpect(status().isBadRequest());
        update(driverA, upcomingA, Map.of("startDate", NOW.minusHours(1).toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void endDateInThePastIsRefused() throws Exception {
        update(agent, startedA, Map.of("endDate", NOW.minusMinutes(5).toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void agentCannotExtendIntoTheNextReservation() throws Exception {
        insertReservation(driverB, otherCar, NOW.plusHours(3), NOW.plusHours(4));

        update(agent, startedA, Map.of("endDate", NOW.plusHours(5).toString()))
            .andExpect(status().isConflict())
            .andExpect(result -> assertEquals(SLOT_TAKEN, result.getResponse().getContentAsString()));
    }

    // Mêmes règles qu'à la création : un véhicule en maintenance ne reçoit pas de nouvelles dates
    @Test
    void reservationCannotMoveOnAVehicleInMaintenance() throws Exception {
        jdbc.update("UPDATE vehicles SET status = 'MAINTENANCE' WHERE id = ?", car);

        update(driverA, upcomingA, Map.of("endDate", DAY2.plusHours(13).toString()))
            .andExpect(status().isConflict());
    }

    @Test
    void notesOnlyChanges() throws Exception {
        update(driverA, upcomingA, Map.of("notesBefore", "Avant le début")).andExpect(status().isOk());
        update(agent, startedA, Map.of("notesBefore", "Pendant la location")).andExpect(status().isOk());
        update(admin, upcomingB, Map.of("notesBefore", "Par l'administrateur")).andExpect(status().isOk());

        assertEquals("Avant le début", column(upcomingA, "notes_before", String.class));
        assertEquals("Pendant la location", column(startedA, "notes_before", String.class));
        assertEquals(admin.getId(), column(upcomingB, "updated_by", UUID.class));
    }

    // --- Écritures simultanées sur la même réservation ---

    // Une annulation en cours (transaction ouverte) : la modification attend son tour,
    // puis voit la réservation annulée, au lieu de la ressusciter en écrasant cancelled_at
    @Test
    void concurrentUpdateWaitsForCancellationThenIsRefused() throws Exception {
        CountDownLatch cancelling = new CountDownLatch(1);
        CountDownLatch releaseCancel = new CountDownLatch(1);

        Future<?> cancellation = executor.submit(() -> inTransaction(() -> {
            jdbc.queryForObject("SELECT id FROM reservations WHERE id = ? FOR NO KEY UPDATE", UUID.class, upcomingA);
            jdbc.update("UPDATE reservations SET cancelled_at = ?, cancelled_by = ? WHERE id = ?",
                NOW, driverA.getId(), upcomingA);
            cancelling.countDown();
            await(releaseCancel);
        }));
        assertTrue(cancelling.await(10, TimeUnit.SECONDS), "L'annulation n'a pas démarré");

        Future<MvcResult> concurrentUpdate = executor.submit(() ->
            update(agent, upcomingA, Map.of("notesBefore", "Écrite pendant l'annulation")).andReturn());

        assertTrue(blockedOnLock("for no key update", concurrentUpdate),
            "La modification doit attendre le verrou de la réservation");

        releaseCancel.countDown();
        cancellation.get(10, TimeUnit.SECONDS);
        assertEquals(409, concurrentUpdate.get(10, TimeUnit.SECONDS).getResponse().getStatus());
        assertNotNull(column(upcomingA, "cancelled_at", OffsetDateTime.class), "L'annulation ne doit pas être écrasée");
    }

    // --- Effets d'une annulation ---

    @Test
    void cancelledReservationFreesItsSlot() throws Exception {
        cancel(driverA, upcomingA).andExpect(status().isOk());

        reserve(driverB, car, DAY2.plusHours(10), DAY2.plusHours(12)).andExpect(status().isCreated());
    }

    @Test
    void cancelledReservationIsFrozen() throws Exception {
        cancel(driverA, upcomingA).andExpect(status().isOk());

        cancel(driverA, upcomingA).andExpect(status().isConflict());
        update(agent, upcomingA, Map.of("notesBefore", "Trop tard")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/reservations/" + upcomingA + "/return")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TestRequests.validReturnVehicleRequest())))
            .andExpect(status().isConflict());
    }

    @Test
    void returnedReservationCannotBeChanged() throws Exception {
        jdbc.update("UPDATE reservations SET returned_at = ? WHERE id = ?", NOW, startedA);

        update(agent, startedA, Map.of("notesBefore", "Trop tard")).andExpect(status().isConflict());
        cancel(agent, startedA).andExpect(status().isConflict());
    }

    // R2 et R6 : les notifications de la réservation disparaissent, une seule notification d'annulation les remplace
    @Test
    void cancellationReplacesNotificationsWithOneCancellationNotice() throws Exception {
        insertNotification(admin, upcomingA, "NEW_RESERVATION");
        insertNotification(agent, upcomingA, "NEW_RESERVATION");
        insertNotification(admin, upcomingB, "NEW_RESERVATION");

        cancel(driverA, upcomingA).andExpect(status().isOk());

        assertEquals(List.of("RESERVATION_CANCELLED"), jdbc.queryForList(
            "SELECT DISTINCT type FROM notifications WHERE reservation_id = ?", String.class, upcomingA));
        assertEquals(Set.of(admin.getId(), agent.getId()), Set.copyOf(jdbc.queryForList(
            "SELECT user_id FROM notifications WHERE reservation_id = ?", UUID.class, upcomingA)));
        assertEquals(2, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE reservation_id = ?", Integer.class, upcomingA));
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE reservation_id = ?", Integer.class, upcomingB),
            "Les notifications des autres réservations restent");
    }

    // Plus aucune notification après l'annulation (ex. « fin proche » du traitement planifié)
    @Test
    void cancelledReservationGetsNoEndingSoonNotice() {
        UUID ending = insertReservation(driverA, car, NOW.minusDays(1), NOW.plusHours(24));
        jdbc.update("UPDATE reservations SET cancelled_at = ? WHERE id = ?", NOW.minusDays(2), ending);

        // Contrôle positif : la même situation, non annulée, est bien notifiée
        UUID notCancelled = insertReservation(driverB, insertVehicle("CC-333-CC"), NOW.minusDays(1), NOW.plusHours(24));

        notificationService.checkEndingSoon();

        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE reservation_id = ?", Integer.class, ending));
        assertEquals(2, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE reservation_id = ?", Integer.class, notCancelled));
    }

    // Une réservation annulée n'occupe pas le véhicule, même si sa période est en cours
    @Test
    void cancelledReservationDoesNotKeepTheVehicleOut() throws Exception {
        UUID cancelledNow = insertReservation(driverA, car, NOW.minusHours(1), NOW.plusHours(1));
        jdbc.update("UPDATE reservations SET cancelled_at = ? WHERE id = ?", NOW.minusDays(1), cancelledNow);

        mockMvc.perform(get("/api/v1/vehicles/" + car).header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // --- Historique : statut calculé et filtre ---

    @Test
    void listFiltersByComputedStatus() throws Exception {
        cancel(driverA, upcomingA).andExpect(status().isOk());

        listWithStatus("CANCELLED")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(upcomingA.toString())))
            .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
        listWithStatus("ACTIVE")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(startedA.toString(), upcomingB.toString())));
        listWithStatus("UPCOMING")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(upcomingB.toString())));
        listWithStatus("IN_PROGRESS")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(startedA.toString())));

        jdbc.update("UPDATE reservations SET returned_at = ? WHERE id = ?", NOW, startedA);
        listWithStatus("RETURNED")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(startedA.toString())))
            .andExpect(jsonPath("$.content[0].status").value("RETURNED"));
    }

    @Test
    void unknownStatusFilterIsRefused() throws Exception {
        listWithStatus("FOO").andExpect(status().isBadRequest());
    }

    // Les compteurs de l'agent ignorent les réservations annulées
    @Test
    void dashboardCountersIgnoreCancelledReservations() throws Exception {
        cancel(agent, upcomingA).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.myStats.totalReservations").value(2))
            .andExpect(jsonPath("$.myStats.activeReservations").value(2));

        // Réservations par agent (vue administrateur) : l'annulée ne compte pas
        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationsByAgent[0].count").value(2));
    }

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    // --- Outils ---

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    // Attend (10 s max) qu'une requête SQL contenant ce texte soit bloquée sur un verrou de PostgreSQL
    private boolean blockedOnLock(String sqlFragment, Future<?> request) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && !request.isDone()) {
            Integer waiting = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query ILIKE ?",
                Integer.class, "%" + sqlFragment + "%");
            if (waiting > 0) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ResultActions update(User actor, UUID reservationId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(patch("/api/v1/reservations/" + reservationId)
            .header(HttpHeaders.AUTHORIZATION, bearer(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions cancel(User actor, UUID reservationId) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
            .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    private ResultActions reserve(User driver, UUID vehicleId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("vehicleId", vehicleId.toString());
        body.put("startDate", start.toString());
        body.put("endDate", end.toString());
        return mockMvc.perform(post("/api/v1/reservations")
            .header(HttpHeaders.AUTHORIZATION, bearer(driver))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions listWithStatus(String status) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations").param("status", status)
            .header(HttpHeaders.AUTHORIZATION, bearer(agent)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private UUID insertVehicle(String plate) {
        return jdbc.queryForObject(
            "INSERT INTO vehicles (tenant_id, plate_number, brand, model, mileage_km, condition, status) "
                + "VALUES (?, ?, 'Toyota', 'Yaris', ?, 'GOOD', 'AVAILABLE') RETURNING id",
            UUID.class, Tenants.DEFAULT_ID, plate, MILEAGE);
    }

    // Agent référent : agent (compté dans ses statistiques). Déjà commencée : le véhicule a été remis
    private UUID insertReservation(User driver, UUID vehicleId, OffsetDateTime start, OffsetDateTime end) {
        return jdbc.queryForObject(
            "INSERT INTO reservations (tenant_id, vehicle_id, driver_id, agent_id, start_date, end_date, "
                + "condition_before, mileage_start, handed_over_at) VALUES (?, ?, ?, ?, ?, ?, 'GOOD', ?, ?) RETURNING id",
            UUID.class, Tenants.DEFAULT_ID, vehicleId, driver.getId(), agent.getId(), start, end, MILEAGE,
            start.isAfter(NOW) ? null : start);
    }

    private void insertNotification(User user, UUID reservationId, String type) {
        jdbc.update("INSERT INTO notifications (tenant_id, user_id, reservation_id, type) VALUES (?, ?, ?, ?)",
            Tenants.DEFAULT_ID, user.getId(), reservationId, type);
    }

    private <T> T column(UUID reservationId, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM reservations WHERE id = ?", type, reservationId);
    }
}
