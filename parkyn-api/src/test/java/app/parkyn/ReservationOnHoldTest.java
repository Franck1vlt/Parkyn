package app.parkyn;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.JwtService;
import app.parkyn.service.NotificationService;
import app.parkyn.tenant.Tenants;
import tools.jackson.databind.ObjectMapper;

/**
 * Retour en retard : tant que le véhicule n'est pas retourné, la réservation suivante passe en attente (ON_HOLD).
 * Elle repasse en cours dès le retour. L'agent la gère (notes, report, annulation) ;
 * agents et administrateurs sont prévenus une fois (vérification toutes les 15 minutes).
 * En attente, la réservation n'a pas vraiment commencé (le véhicule n'a pas été remis) :
 * l'agent peut encore la reporter ou l'annuler.
 */
@AutoConfigureMockMvc
@IntegrationTest
class ReservationOnHoldTest {

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    private static final int MILEAGE = 50000;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired NotificationService notificationService;
    @Autowired ObjectMapper objectMapper;

    private User driverA;
    private User driverB;
    private User agent;
    private User admin;
    private UUID car;

    // late : commencée il y a 3 h, devait finir il y a 1 h, véhicule pas retourné
    // waiting : la suivante sur le même véhicule, son début est passé
    private UUID late;
    private UUID waiting;

    @BeforeEach
    void setUp() {
        TestUsers testUsers = new TestUsers(userRepository, jdbc, passwordEncoder);
        driverA = testUsers.createWithoutPassword("driverA@parkyn.app", User.Role.DRIVER);
        driverB = testUsers.createWithoutPassword("driverB@parkyn.app", User.Role.DRIVER);
        agent = testUsers.createWithoutPassword("agent@parkyn.app", User.Role.AGENT);
        admin = testUsers.createWithoutPassword("admin@parkyn.app", User.Role.ADMIN);
        car = insertVehicle("AA-111-AA");

        late = insertReservation(driverA, car, NOW.minusHours(3), NOW.minusHours(1));
        waiting = insertReservation(driverB, car, NOW.minusMinutes(30), NOW.plusHours(2));
    }

    // --- Statut calculé ---

    @Test
    void nextReservationIsOnHoldWhileVehicleIsNotReturned() throws Exception {
        statusOf(waiting).andExpect(jsonPath("$.status").value("ON_HOLD"));
        statusOf(late).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void onHoldEndsWhenTheVehicleIsReturned() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/" + late + "/return")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TestRequests.validReturnVehicleRequest())))
            .andExpect(status().isOk());

        statusOf(waiting).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // Pas encore commencée : rien n'attend encore
    @Test
    void futureReservationIsStillUpcoming() throws Exception {
        UUID future = insertReservation(driverB, car, NOW.plusHours(3), NOW.plusHours(4));

        statusOf(future).andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    // Seule une réservation antérieure du MÊME véhicule, non retournée et non annulée, met en attente
    @Test
    void onlyAnUnreturnedEarlierReservationOfTheSameVehicleHolds() throws Exception {
        UUID otherCar = insertVehicle("BB-222-BB");
        UUID elsewhere = insertReservation(driverB, otherCar, NOW.minusMinutes(30), NOW.plusHours(2));
        statusOf(elsewhere).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        jdbc.update("UPDATE reservations SET cancelled_at = ? WHERE id = ?", NOW.minusDays(1), late);
        statusOf(waiting).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void listFiltersOnHoldApartFromInProgress() throws Exception {
        listWithStatus("ON_HOLD")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(waiting.toString())))
            .andExpect(jsonPath("$.content[0].status").value("ON_HOLD"));
        listWithStatus("IN_PROGRESS")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(late.toString())));
        listWithStatus("ACTIVE")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(late.toString(), waiting.toString())));
    }

    @Test
    void dashboardShowsOnHoldStatus() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestReservations[?(@.id == '" + waiting + "')].status").value("ON_HOLD"));
    }

    // --- L'agent gère la réservation en attente ---

    @Test
    void agentReschedulesAReservationOnHold() throws Exception {
        update(agent, waiting, Map.of(
                "startDate", NOW.plusHours(1).toString(),
                "endDate", NOW.plusHours(3).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    @Test
    void agentAddsNotesToAReservationOnHold() throws Exception {
        update(agent, waiting, Map.of("notesBefore", "Véhicule précédent en retard, client prévenu"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ON_HOLD"));
    }

    @Test
    void agentCancelsAReservationOnHold() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/" + waiting + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // Le conducteur ne modifie que avant le début (R3), même en attente
    @Test
    void driverCannotChangeTheirReservationOnHold() throws Exception {
        update(driverB, waiting, Map.of("notesBefore", "Je patiente")).andExpect(status().isConflict());
    }

    // R3 : le conducteur n'annule pas non plus après la date de début, même en attente
    @Test
    void driverCannotCancelTheirReservationOnHold() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/" + waiting + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(driverB)))
            .andExpect(status().isConflict());
    }

    // Le véhicule n'a jamais été remis : pas de retour, sinon il écraserait le kilométrage du véhicule encore dehors
    @Test
    void reservationOnHoldCannotBeReturned() throws Exception {
        mockMvc.perform(post("/api/v1/reservations/" + waiting + "/return")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(TestRequests.validReturnVehicleRequest())))
            .andExpect(status().isConflict());

        assertEquals(MILEAGE, jdbc.queryForObject("SELECT mileage_km FROM vehicles WHERE id = ?", Integer.class, car));
    }

    @Test
    void reservationOnHoldCannotBeRescheduledIntoATakenSlot() throws Exception {
        insertReservation(driverA, car, NOW.plusHours(3), NOW.plusHours(4));

        update(agent, waiting, Map.of("startDate", NOW.plusHours(2).toString(), "endDate", NOW.plusHours(5).toString()))
            .andExpect(status().isConflict());
    }

    // Pas vraiment commencée : mêmes règles qu'une réservation à venir, le véhicule en maintenance la bloque
    @Test
    void reservationOnHoldCannotMoveOnAVehicleInMaintenance() throws Exception {
        jdbc.update("UPDATE vehicles SET status = 'MAINTENANCE' WHERE id = ?", car);

        update(agent, waiting, Map.of("endDate", NOW.plusHours(3).toString())).andExpect(status().isConflict());
    }

    // Chaîne : la troisième attend derrière la deuxième, elle-même en attente
    @Test
    void reservationsQueueBehindEachOther() throws Exception {
        jdbc.update("UPDATE reservations SET end_date = ? WHERE id = ?", NOW.minusMinutes(20), waiting);
        UUID third = insertReservation(driverA, car, NOW.minusMinutes(10), NOW.plusHours(1));

        statusOf(third).andExpect(jsonPath("$.status").value("ON_HOLD"));
    }

    @Test
    void cancellingAReservationOnHoldRemovesItsOnHoldNotice() throws Exception {
        notificationService.checkOnHold();

        mockMvc.perform(post("/api/v1/reservations/" + waiting + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk());

        assertEquals(List.of("RESERVATION_CANCELLED"), jdbc.queryForList(
            "SELECT DISTINCT type FROM notifications WHERE reservation_id = ?", String.class, waiting));
    }

    // Report : pas dans le passé
    @Test
    void reservationOnHoldCannotBeRescheduledInThePast() throws Exception {
        update(agent, waiting, Map.of("startDate", NOW.minusMinutes(10).toString()))
            .andExpect(status().isBadRequest());
    }

    // --- Notification : agents et administrateurs, une seule fois ---

    @Test
    void staffIsNotifiedOnceWhenAReservationGoesOnHold() {
        notificationService.checkOnHold();
        notificationService.checkOnHold();

        assertEquals(Set.of(admin.getId(), agent.getId()), Set.copyOf(jdbc.queryForList(
            "SELECT user_id FROM notifications WHERE reservation_id = ? AND type = 'RESERVATION_ON_HOLD'",
            UUID.class, waiting)));
        assertEquals(2, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE type = 'RESERVATION_ON_HOLD'", Integer.class),
            "Une seule notification par destinataire, et rien pour la réservation en retard elle-même");
    }

    // N3 : vérification toutes les 15 minutes (le traitement des fins proches reste quotidien)
    @Test
    void onHoldCheckRunsEveryFifteenMinutes() throws Exception {
        Scheduled schedule = NotificationService.class.getMethod("checkOnHold").getAnnotation(Scheduled.class);

        assertEquals("0 */15 * * * *", schedule.cron());
    }

    @Test
    void noNoticeWhenNothingIsOnHold() {
        jdbc.update("UPDATE reservations SET returned_at = ? WHERE id = ?", NOW, late);

        notificationService.checkOnHold();

        assertEquals(List.of(), jdbc.queryForList(
            "SELECT id FROM notifications WHERE type = 'RESERVATION_ON_HOLD'", UUID.class));
    }

    // --- Outils ---

    private ResultActions statusOf(UUID reservationId) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk());
    }

    private ResultActions listWithStatus(String status) throws Exception {
        return mockMvc.perform(get("/api/v1/reservations").param("status", status)
                .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
            .andExpect(status().isOk());
    }

    private ResultActions update(User actor, UUID reservationId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(patch("/api/v1/reservations/" + reservationId)
            .header(HttpHeaders.AUTHORIZATION, bearer(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
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

    private UUID insertReservation(User driver, UUID vehicleId, OffsetDateTime start, OffsetDateTime end) {
        return jdbc.queryForObject(
            "INSERT INTO reservations (tenant_id, vehicle_id, driver_id, agent_id, start_date, end_date, "
                + "condition_before, mileage_start) VALUES (?, ?, ?, ?, ?, ?, 'GOOD', ?) RETURNING id",
            UUID.class, Tenants.DEFAULT_ID, vehicleId, driver.getId(), agent.getId(), start, end, MILEAGE);
    }
}
