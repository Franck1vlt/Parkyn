package app.parkyn;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.JwtService;
import app.parkyn.tenant.Tenants;
import tools.jackson.databind.ObjectMapper;

/**
 * Disponibilité d'un véhicule : décidée par les périodes réservées, pas par un statut stocké.
 * Le statut RESERVED est calculé : réservé pendant une réservation (ou tant que le véhicule n'est pas
 * retourné), disponible le reste du temps. MAINTENANCE et RETIRED restent des statuts posés à la main.
 */
@AutoConfigureMockMvc
@IntegrationTest
class VehicleAvailabilityTest {

    private static final String SLOT_TAKEN =
        "Véhicule déjà réservé sur ce créneau : choisissez un autre véhicule ou une autre plage horaire";
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    // Demain, heures pleines : 13 h - 15 h puis 16 h - 18 h (l'exemple de la décision R1)
    private static final OffsetDateTime TOMORROW = NOW.plusDays(1).truncatedTo(ChronoUnit.DAYS);
    private static final OffsetDateTime H13 = TOMORROW.plusHours(13);
    private static final OffsetDateTime H14 = TOMORROW.plusHours(14);
    private static final OffsetDateTime H15 = TOMORROW.plusHours(15);
    private static final OffsetDateTime H16 = TOMORROW.plusHours(16);
    private static final OffsetDateTime H18 = TOMORROW.plusHours(18);
    private static final int MILEAGE = 50000;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private User driver;
    private User agent;
    private UUID car;
    private UUID otherCar;
    private UUID carInMaintenance;

    @BeforeEach
    void setUp() {
        TestUsers testUsers = new TestUsers(userRepository, jdbc, passwordEncoder);
        driver = testUsers.createWithoutPassword("driver@parkyn.app", User.Role.DRIVER);
        agent = testUsers.createWithoutPassword("agent@parkyn.app", User.Role.AGENT);
        car = insertVehicle("AA-111-AA", "AVAILABLE");
        otherCar = insertVehicle("BB-222-BB", "AVAILABLE");
        carInMaintenance = insertVehicle("CC-333-CC", "MAINTENANCE");
    }

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    // --- Réserver : seule la période décide ---

    @Test
    void sameVehicleCanBeReservedOnTwoSlotsOfTheSameDay() throws Exception {
        reserve(car, H13, H15).andExpect(status().isCreated());
        reserve(car, H16, H18).andExpect(status().isCreated());

        assertEquals("AVAILABLE", storedStatusOf(car), "La création ne doit plus modifier le statut stocké");
    }

    @Test
    void overlappingSlotIsRefusedWithAdvice() throws Exception {
        reserve(car, H13, H15).andExpect(status().isCreated());

        reserve(car, H14, H16)
            .andExpect(status().isConflict())
            .andExpect(result -> assertEquals(SLOT_TAKEN, result.getResponse().getContentAsString()));
        assertEquals(1, countReservationsOf(car));
    }

    // Bornes : la fin d'une réservation est libre pour la suivante
    @Test
    void adjacentSlotIsAllowed() throws Exception {
        reserve(car, H13, H15).andExpect(status().isCreated());

        reserve(car, H15, H16).andExpect(status().isCreated());
    }

    @Test
    void vehicleInMaintenanceCannotBeReserved() throws Exception {
        reserve(carInMaintenance, H13, H15).andExpect(status().isConflict());
    }

    // N2 : un retour en avance libère le reste du créneau
    @Test
    void earlyReturnFreesTheRestOfTheSlot() throws Exception {
        UUID reservation = insertReservation(car, NOW.minusHours(2), NOW.plusHours(2));
        jdbc.update("UPDATE reservations SET returned_at = ? WHERE id = ?", NOW.minusHours(1), reservation);

        reserve(car, NOW.plusMinutes(10), NOW.plusHours(1)).andExpect(status().isCreated());
    }

    // --- Statut affiché : calculé à l'instant présent ---

    @Test
    void vehicleIsAvailableBeforeItsReservation() throws Exception {
        reserve(car, H13, H15).andExpect(status().isCreated());

        statusShownFor(car).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void vehicleIsReservedDuringItsReservation() throws Exception {
        insertReservation(car, NOW.minusHours(1), NOW.plusHours(1));

        statusShownFor(car).andExpect(jsonPath("$.status").value("RESERVED"));
    }

    // Retour en retard : tant qu'il n'est pas retourné, le véhicule reste dehors
    @Test
    void lateUnreturnedVehicleStaysReserved() throws Exception {
        insertReservation(car, NOW.minusHours(3), NOW.minusHours(1));

        statusShownFor(car).andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void returnedVehicleIsAvailableAgain() throws Exception {
        UUID reservation = insertReservation(car, NOW.minusHours(1), NOW.plusHours(1));

        returnVehicle(reservation).andExpect(status().isOk());

        statusShownFor(car).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    // Le retour ne touche plus au statut stocké : un véhicule passé en maintenance pendant la location y reste
    @Test
    void returnKeepsMaintenance() throws Exception {
        UUID reservation = insertReservation(car, NOW.minusHours(1), NOW.plusHours(1));
        jdbc.update("UPDATE vehicles SET status = 'MAINTENANCE' WHERE id = ?", car);

        returnVehicle(reservation).andExpect(status().isOk());

        assertEquals("MAINTENANCE", storedStatusOf(car));
        statusShownFor(car).andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void listStatusFilterUsesComputedStatus() throws Exception {
        insertReservation(car, NOW.minusHours(1), NOW.plusHours(1));

        mockMvc.perform(get("/api/v1/vehicles").param("status", "RESERVED")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(car.toString()))
            .andExpect(jsonPath("$.content[0].status").value("RESERVED"));

        mockMvc.perform(get("/api/v1/vehicles").param("status", "AVAILABLE")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(otherCar.toString()))
            .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"));
    }

    @Test
    void reservedStatusCannotBeSetByHand() throws Exception {
        mockMvc.perform(patch("/api/v1/vehicles/" + car + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESERVED\"}"))
            .andExpect(status().isUnprocessableContent());

        assertEquals("AVAILABLE", storedStatusOf(car));
    }

    // --- N1 : véhicules libres sur une plage horaire ---

    @Test
    void periodFilterListsOnlyVehiclesFreeOnThatSlot() throws Exception {
        reserve(car, H13, H15).andExpect(status().isCreated());

        freeBetween(H14, H16)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(otherCar.toString())));

        freeBetween(H16, H18)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(car.toString(), otherCar.toString())));
    }

    @Test
    void periodFilterNeedsBothBoundsInOrder() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles").param("from", H13.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isBadRequest());

        freeBetween(H15, H13).andExpect(status().isBadRequest());
    }

    // --- Demandes simultanées : traitées l'une après l'autre ---

    // File d'attente : une première demande tient le véhicule (même verrou que l'application) ;
    // la seconde attend sur ce verrou, puis passe quand la première se termine
    @Test
    void simultaneousRequestWaitsForTheVehicleLock() throws Exception {
        CountDownLatch firstHoldsVehicle = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> inTransaction(() -> {
            jdbc.queryForObject("SELECT id FROM vehicles WHERE id = ? FOR NO KEY UPDATE", UUID.class, car);
            firstHoldsVehicle.countDown();
            await(releaseFirst);
        }));
        assertTrue(firstHoldsVehicle.await(10, TimeUnit.SECONDS), "La première demande n'a pas démarré");

        Future<MvcResult> second = executor.submit(() -> reserve(car, H13, H15).andReturn());

        assertTrue(blockedOnLock("for no key update", second), "La seconde demande doit attendre le verrou du véhicule");

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        assertEquals(201, second.get(10, TimeUnit.SECONDS).getResponse().getStatus());
    }

    // Filet de sécurité : une réservation écrite sans passer par le verrou (ici en SQL, transaction ouverte)
    // est invisible pour la vérification ; la contrainte de la base refuse alors le chevauchement, même réponse
    @Test
    void writeBypassingTheLockStillGetsConflict() throws Exception {
        CountDownLatch firstWrote = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> inTransaction(() -> {
            insertReservation(car, H13, H15);
            firstWrote.countDown();
            await(releaseFirst);
        }));
        assertTrue(firstWrote.await(10, TimeUnit.SECONDS), "La première écriture n'a pas démarré");

        Future<MvcResult> second = executor.submit(() -> reserve(car, H14, H16).andReturn());

        assertTrue(blockedOnLock("insert into reservations", second), "L'insertion doit attendre la contrainte");

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);
        MvcResult result = second.get(10, TimeUnit.SECONDS);
        assertEquals(409, result.getResponse().getStatus());
        assertEquals(SLOT_TAKEN, result.getResponse().getContentAsString());
        assertEquals(1, countReservationsOf(car));
    }

    // --- Cas limites ---

    // Retournée avant même son début : la réservation n'occupe rien
    @Test
    void reservationReturnedBeforeItsStartDoesNotBlock() throws Exception {
        UUID reservation = insertReservation(car, H13, H15);
        jdbc.update("UPDATE reservations SET returned_at = ? WHERE id = ?", NOW, reservation);

        reserve(car, H13, H15).andExpect(status().isCreated());
    }

    // Même cas, avec une plage qui commence avant l'heure du retour (seul cas où la règle le distingue)
    @Test
    void reservationReturnedBeforeItsStartFreesAnySlot() throws Exception {
        UUID reservation = insertReservation(car, H13, H15);
        jdbc.update("UPDATE reservations SET returned_at = ? WHERE id = ?", NOW, reservation);

        freeBetween(NOW.minusHours(1), H14)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(car.toString(), otherCar.toString())));
    }

    @Test
    void maintenanceFilterUsesStoredStatus() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles").param("status", "MAINTENANCE")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(carInMaintenance.toString()));
    }

    // Avec une plage horaire, AVAILABLE veut dire « libre sur la plage » : un véhicule dehors aujourd'hui
    // reste proposé pour demain
    @Test
    void periodFilterWithAvailableStatusIgnoresCurrentUse() throws Exception {
        insertReservation(car, NOW.minusHours(1), NOW.plusHours(1));

        mockMvc.perform(get("/api/v1/vehicles")
                .param("status", "AVAILABLE")
                .param("from", H13.toString())
                .param("to", H15.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(car.toString(), otherCar.toString())));
    }

    @Test
    void periodFilterHonorsEarlyReturn() throws Exception {
        UUID reservation = insertReservation(car, NOW.minusHours(2), NOW.plusHours(2));
        jdbc.update("UPDATE reservations SET returned_at = ? WHERE id = ?", NOW.minusHours(1), reservation);

        freeBetween(NOW.plusMinutes(10), NOW.plusHours(1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(car.toString(), otherCar.toString())));
    }

    // Sans fuseau horaire, l'heure serait ambiguë : refusée
    @Test
    void periodWithoutOffsetIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles")
                .param("from", "2030-01-01T13:00:00")
                .param("to", "2030-01-01T15:00:00")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isBadRequest());
    }

    // --- Outils ---

    private org.springframework.test.web.servlet.ResultActions reserve(UUID vehicleId, OffsetDateTime start,
                                                                      OffsetDateTime end) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("vehicleId", vehicleId.toString());
        body.put("startDate", start.toString());
        body.put("endDate", end.toString());
        return mockMvc.perform(post("/api/v1/reservations")
            .header(HttpHeaders.AUTHORIZATION, bearer(driver))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions returnVehicle(UUID reservationId) throws Exception {
        return mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/return")
            .header(HttpHeaders.AUTHORIZATION, bearer(agent))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(TestRequests.validReturnVehicleRequest())));
    }

    private org.springframework.test.web.servlet.ResultActions statusShownFor(UUID vehicleId) throws Exception {
        return mockMvc.perform(get("/api/v1/vehicles/" + vehicleId)
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions freeBetween(OffsetDateTime from,
                                                                          OffsetDateTime to) throws Exception {
        return mockMvc.perform(get("/api/v1/vehicles")
            .param("from", from.toString())
            .param("to", to.toString())
            .header(HttpHeaders.AUTHORIZATION, bearer(agent)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private UUID insertVehicle(String plate, String status) {
        return jdbc.queryForObject(
            "INSERT INTO vehicles (tenant_id, plate_number, brand, model, mileage_km, condition, status) "
                + "VALUES (?, ?, 'Toyota', 'Yaris', ?, 'GOOD', ?) RETURNING id",
            UUID.class, Tenants.DEFAULT_ID, plate, MILEAGE, status);
    }

    // Réservation déjà commencée : le véhicule a été remis (il est dehors)
    private UUID insertReservation(UUID vehicleId, OffsetDateTime start, OffsetDateTime end) {
        return jdbc.queryForObject(
            "INSERT INTO reservations (tenant_id, vehicle_id, driver_id, agent_id, start_date, end_date, "
                + "condition_before, mileage_start, handed_over_at) VALUES (?, ?, ?, ?, ?, ?, 'GOOD', ?, ?) RETURNING id",
            UUID.class, Tenants.DEFAULT_ID, vehicleId, driver.getId(), agent.getId(), start, end, MILEAGE,
            start.isAfter(NOW) ? null : start);
    }

    private String storedStatusOf(UUID vehicleId) {
        return jdbc.queryForObject("SELECT status FROM vehicles WHERE id = ?", String.class, vehicleId);
    }

    private int countReservationsOf(UUID vehicleId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE vehicle_id = ?", Integer.class, vehicleId);
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    // Attend (10 s max) qu'une requête SQL contenant ce texte soit bloquée sur un verrou de PostgreSQL,
    // tant que la demande HTTP n'est pas terminée. Plus fiable qu'un délai fixe
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
}
