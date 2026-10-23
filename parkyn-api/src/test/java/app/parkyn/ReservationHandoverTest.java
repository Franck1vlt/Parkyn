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
import java.util.Map;
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
 * Remise du véhicule (état des lieux de départ et remise des clés), décisions H1 à H5 :
 * - l'agent saisit le kilométrage ; l'état de départ est repris du véhicule ;
 * - à partir de l'heure du rendez-vous de début, et jusqu'au rendez-vous de fin ;
 * - la réservation passe alors « en cours » et le véhicule est dehors ;
 * - avant la remise, une réservation commencée est « à remettre » : l'agent peut la reporter
 *   ou l'annuler (conducteur absent), et le véhicule reste disponible.
 */
@AutoConfigureMockMvc
@IntegrationTest
class ReservationHandoverTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    private static final int MILEAGE = 50000;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired NotificationService notificationService;
    @Autowired PlatformTransactionManager transactionManager;

    private User driver;
    private User agent;
    private User admin;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private UUID car;

    // due : rendez-vous de début passé depuis 10 minutes, clés pas encore remises
    private UUID due;

    @BeforeEach
    void setUp() {
        TestUsers testUsers = new TestUsers(userRepository, jdbc, passwordEncoder);
        driver = testUsers.createWithoutPassword("driver@parkyn.app", User.Role.DRIVER);
        agent = testUsers.createWithoutPassword("agent@parkyn.app", User.Role.AGENT);
        admin = testUsers.createWithoutPassword("admin@parkyn.app", User.Role.ADMIN);
        car = insertVehicle("AA-111-AA");
        due = insertReservation(car, NOW.minusMinutes(10), NOW.plusHours(2));
    }

    // --- Avant la remise ---

    @Test
    void startedReservationAwaitsHandover() throws Exception {
        statusOf(due).andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));
    }

    // Le conducteur n'est pas venu chercher les clés : le véhicule n'est pas dehors
    @Test
    void vehicleStaysAvailableUntilHandover() throws Exception {
        vehicleStatusOf(car).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void creationDoesNotRecordMileage() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("vehicleId", car.toString());
        body.put("startDate", NOW.plusDays(1).toString());
        body.put("endDate", NOW.plusDays(1).plusHours(2).toString());

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(driver))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mileageStart").doesNotExist())
            .andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    // --- La remise ---

    @Test
    void agentHandsOverTheVehicle() throws Exception {
        handOver(agent, due, 50120)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.mileageStart").value(50120))
            // État de départ repris du véhicule (H1), pas saisi
            .andExpect(jsonPath("$.conditionBefore").value("FAIR"));

        assertNotNull(column(due, "handed_over_at", OffsetDateTime.class));
        assertEquals(agent.getId(), column(due, "handed_over_by", UUID.class));
        assertEquals(50120, jdbc.queryForObject("SELECT mileage_km FROM vehicles WHERE id = ?", Integer.class, car));
        vehicleStatusOf(car).andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void mileageCannotGoBackwards() throws Exception {
        handOver(agent, due, MILEAGE - 1).andExpect(status().isBadRequest());

        assertNull(column(due, "handed_over_at", OffsetDateTime.class));
    }

    @Test
    void mileageIsRequired() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/" + due + "/handover")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // H2 : l'heure de début est le rendez-vous de remise des clés
    @Test
    void handoverWaitsForTheAppointment() throws Exception {
        UUID tomorrow = insertReservation(car, NOW.plusDays(1), NOW.plusDays(1).plusHours(2));

        handOver(agent, tomorrow, MILEAGE).andExpect(status().isConflict());
    }

    // Rendez-vous de retour dépassé : il faut reporter (ou annuler) la réservation
    @Test
    void handoverAfterTheReturnAppointmentIsRefused() throws Exception {
        UUID missed = insertReservation(insertVehicle("BB-222-BB"), NOW.minusHours(3), NOW.minusHours(1));

        handOver(agent, missed, MILEAGE).andExpect(status().isConflict());
    }

    @Test
    void vehicleCannotBeHandedOverTwice() throws Exception {
        handOver(agent, due, MILEAGE).andExpect(status().isOk());

        handOver(agent, due, MILEAGE + 10).andExpect(status().isConflict());
    }

    // Véhicule encore dehors avec le conducteur précédent (réservation en attente)
    @Test
    void vehicleStillOutCannotBeHandedOver() throws Exception {
        UUID late = insertReservation(car, NOW.minusHours(4), NOW.minusHours(1));
        jdbc.update("UPDATE reservations SET handed_over_at = start_date WHERE id = ?", late);

        statusOf(due).andExpect(jsonPath("$.status").value("ON_HOLD"));
        handOver(agent, due, MILEAGE).andExpect(status().isConflict());
    }

    @Test
    void vehicleInMaintenanceCannotBeHandedOver() throws Exception {
        jdbc.update("UPDATE vehicles SET status = 'MAINTENANCE' WHERE id = ?", car);

        handOver(agent, due, MILEAGE).andExpect(status().isConflict());
    }

    @Test
    void cancelledReservationCannotBeHandedOver() throws Exception {
        jdbc.update("UPDATE reservations SET cancelled_at = ? WHERE id = ?", NOW, due);

        handOver(agent, due, MILEAGE).andExpect(status().isConflict());
    }

    // H3 : seul un agent ou un administrateur remet les clés
    @Test
    void driverCannotHandOver() throws Exception {
        handOver(driver, due, MILEAGE).andExpect(status().isForbidden());
    }

    @Test
    void unknownReservationIsNotFound() throws Exception {
        handOver(agent, UUID.randomUUID(), MILEAGE).andExpect(status().isNotFound());
    }

    @Test
    void adminHandsOverTheVehicle() throws Exception {
        handOver(admin, due, MILEAGE).andExpect(status().isOk());

        assertEquals(admin.getId(), column(due, "handed_over_by", UUID.class));
    }

    @Test
    void vehicleWithUnknownConditionCannotBeHandedOver() throws Exception {
        jdbc.update("UPDATE vehicles SET condition = NULL WHERE id = ?", car);

        handOver(agent, due, MILEAGE).andExpect(status().isConflict());
    }

    // Un refus ne laisse aucune trace : ni remise, ni kilométrage modifié
    @Test
    void refusedHandoverChangesNothing() throws Exception {
        jdbc.update("UPDATE vehicles SET status = 'MAINTENANCE' WHERE id = ?", car);

        handOver(agent, due, MILEAGE + 500).andExpect(status().isConflict());

        assertNull(column(due, "handed_over_by", UUID.class));
        assertEquals(MILEAGE, jdbc.queryForObject("SELECT mileage_km FROM vehicles WHERE id = ?", Integer.class, car));
    }

    // Une réservation à venir (non remise) du même véhicule ne le rend pas indisponible
    @Test
    void laterReservationOfTheSameVehicleDoesNotHold() throws Exception {
        insertReservation(car, NOW.plusDays(1), NOW.plusDays(1).plusHours(2));

        statusOf(due).andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));
        handOver(agent, due, MILEAGE).andExpect(status().isOk());
    }

    // Le conducteur précédent n'est jamais venu (réservation antérieure jamais remise) : le véhicule est là,
    // la réservation suivante n'attend pas
    @Test
    void earlierNoShowDoesNotHold() throws Exception {
        insertReservation(car, NOW.minusHours(3), NOW.minusHours(2));

        statusOf(due).andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));
        handOver(agent, due, MILEAGE).andExpect(status().isOk());
    }

    // Conducteur absent, et le véhicule est déjà parti avec la réservation suivante : ce n'est pas
    // une attente (aucun retard du conducteur précédent), aucune notification d'attente
    @Test
    void noShowIsNotOnHoldBehindALaterReservation() throws Exception {
        UUID noShow = insertReservation(insertVehicle("CC-333-CC"), NOW.minusHours(5), NOW.minusHours(4));
        UUID car3 = jdbc.queryForObject("SELECT vehicle_id FROM reservations WHERE id = ?", UUID.class, noShow);
        UUID next = insertReservation(car3, NOW.minusHours(1), NOW.plusHours(1));
        jdbc.update("UPDATE reservations SET handed_over_at = start_date WHERE id = ?", next);

        statusOf(noShow).andExpect(jsonPath("$.status").value("AWAITING_HANDOVER"));
        notificationService.checkOnHold();
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE reservation_id = ?", Integer.class, noShow));
    }

    // Deux remises presque simultanées : la seconde attend le verrou du véhicule, puis voit que
    // le véhicule vient d'être remis à la réservation précédente (retard) et refuse
    @Test
    void concurrentHandoverWaitsForTheVehicleThenSeesItOut() throws Exception {
        UUID previous = insertReservation(car, NOW.minusHours(3), NOW.minusHours(2));
        CountDownLatch handingOver = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> inTransaction(() -> {
            jdbc.queryForObject("SELECT id FROM vehicles WHERE id = ? FOR NO KEY UPDATE", UUID.class, car);
            jdbc.update("UPDATE reservations SET handed_over_at = ? WHERE id = ?", NOW.minusHours(3), previous);
            handingOver.countDown();
            await(release);
        }));
        assertTrue(handingOver.await(10, TimeUnit.SECONDS), "La première remise n'a pas démarré");

        Future<MvcResult> second = executor.submit(() -> handOver(agent, due, MILEAGE).andReturn());
        assertTrue(blockedOnLock("for no key update", second), "La seconde remise doit attendre le verrou du véhicule");

        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertEquals(409, second.get(10, TimeUnit.SECONDS).getResponse().getStatus());
        assertNull(column(due, "handed_over_at", OffsetDateTime.class));
    }

    // Filtres : à remettre et en cours se distinguent par la remise
    @Test
    void listFiltersSeparateAwaitingFromInProgress() throws Exception {
        listWithStatus("AWAITING_HANDOVER").andExpect(jsonPath("$.content[*].id", containsInAnyOrder(due.toString())));
        listWithStatus("IN_PROGRESS").andExpect(jsonPath("$.content.length()").value(0));

        handOver(agent, due, MILEAGE).andExpect(status().isOk());

        listWithStatus("AWAITING_HANDOVER").andExpect(jsonPath("$.content.length()").value(0));
        listWithStatus("IN_PROGRESS").andExpect(jsonPath("$.content[*].id", containsInAnyOrder(due.toString())));
    }

    // Rappel « fin proche » : seulement pour un véhicule remis
    @Test
    void endingSoonNoticeOnlyForHandedOverReservations() throws Exception {
        UUID ending = insertReservation(insertVehicle("DD-444-DD"), NOW.minusDays(1), NOW.plusHours(24));

        notificationService.checkEndingSoon();

        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE reservation_id = ?", Integer.class, ending));
    }

    // --- Après la remise ---

    @Test
    void returnNeedsAHandover() throws Exception {
        returnVehicle(due).andExpect(status().isConflict());

        handOver(agent, due, MILEAGE).andExpect(status().isOk());
        returnVehicle(due).andExpect(status().isOk());
    }

    // Le kilométrage de retour se compare à celui saisi à la remise
    @Test
    void returnMileageCannotBeBelowHandoverMileage() throws Exception {
        handOver(agent, due, 50300).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservations/" + due + "/return")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conditionAfter\":\"GOOD\",\"mileageEnd\":50200,\"damageReported\":false}"))
            .andExpect(status().isBadRequest());
    }

    // Une fois le véhicule remis, le début est figé et l'annulation n'est plus possible
    @Test
    void handedOverReservationIsLockedInPlace() throws Exception {
        handOver(agent, due, MILEAGE).andExpect(status().isOk());

        update(agent, due, Map.of("startDate", NOW.plusHours(1).toString())).andExpect(status().isConflict());
        cancel(agent, due).andExpect(status().isConflict());
        update(agent, due, Map.of("endDate", NOW.plusHours(3).toString())).andExpect(status().isOk());
    }

    // --- Conducteur absent (cas 2) ---

    // Jamais venu : l'agent annule, même après le rendez-vous ; le véhicule est disponible
    @Test
    void agentCancelsANoShow() throws Exception {
        cancel(agent, due)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        vehicleStatusOf(car).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // R3 : le conducteur, lui, n'annule qu'avant le début
    @Test
    void driverCannotCancelAfterTheAppointment() throws Exception {
        cancel(driver, due).andExpect(status().isConflict());
    }

    // L'agent peut reporter une réservation pas encore remise (début déjà passé)
    @Test
    void agentReschedulesAReservationAwaitingHandover() throws Exception {
        update(agent, due, Map.of(
                "startDate", NOW.plusHours(1).toString(),
                "endDate", NOW.plusHours(3).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    // --- Outils ---

    private ResultActions listWithStatus(String status) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations").param("status", status)
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk());
    }

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

    private ResultActions handOver(User actor, UUID reservationId, int mileage) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/handover")
            .header(HttpHeaders.AUTHORIZATION, bearer(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"mileageStart\":" + mileage + "}"));
    }

    private ResultActions returnVehicle(UUID reservationId) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/return")
            .header(HttpHeaders.AUTHORIZATION, bearer(agent))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"conditionAfter\":\"GOOD\",\"mileageEnd\":" + (MILEAGE + 100) + ",\"damageReported\":false}"));
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

    private ResultActions statusOf(UUID reservationId) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk());
    }

    private ResultActions vehicleStatusOf(UUID vehicleId) throws Exception {
        return mockMvc.perform(get("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    // État FAIR : distinct du GOOD par défaut des réservations insérées, pour vérifier la reprise à la remise
    private UUID insertVehicle(String plate) {
        return jdbc.queryForObject(
            "INSERT INTO vehicles (tenant_id, plate_number, brand, model, mileage_km, condition, status) "
                + "VALUES (?, ?, 'Toyota', 'Yaris', ?, 'FAIR', 'AVAILABLE') RETURNING id",
            UUID.class, Tenants.DEFAULT_ID, plate, MILEAGE);
    }

    // Pas encore remise : ni kilométrage de départ ni remise
    private UUID insertReservation(UUID vehicleId, OffsetDateTime start, OffsetDateTime end) {
        return jdbc.queryForObject(
            "INSERT INTO reservations (tenant_id, vehicle_id, driver_id, agent_id, start_date, end_date, condition_before) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'GOOD') RETURNING id",
            UUID.class, Tenants.DEFAULT_ID, vehicleId, driver.getId(), agent.getId(), start, end);
    }

    private <T> T column(UUID reservationId, String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM reservations WHERE id = ?", type, reservationId);
    }
}
