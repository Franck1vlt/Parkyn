package app.parkyn.security;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import app.parkyn.IntegrationTest;
import app.parkyn.TestRequests;
import app.parkyn.TestUsers;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;
import app.parkyn.service.NotificationService;
import app.parkyn.tenant.Tenants;
import tools.jackson.databind.ObjectMapper;

/**
 * Isolation entre entreprises (ADR-0002, filtre tenant_id systématique) :
 * un utilisateur de l'entreprise B ne lit ni ne modifie jamais une donnée de l'entreprise A, et inversement.
 * Les véhicules et réservations sont insérés en SQL, avec leur tenant, sans passer par le code testé.
 */
@AutoConfigureMockMvc
@IntegrationTest
class TenantIsolationTest {

    // A n'est pas le tenant par défaut : un repli silencieux vers le tenant par défaut ferait échouer les tests
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final OffsetDateTime T0 = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    private static final int MILEAGE = 50000;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired NotificationService notificationService;
    @Autowired ObjectMapper objectMapper;

    private TestUsers testUsers;

    private User adminA;
    private User agentA;
    private User driverA;
    private User disabledA;
    private UUID vehicleA;
    private UUID reservationA;
    private UUID notificationA;

    private User adminB;
    private User agentB;
    private User driverB;
    private UUID vehicleB;
    private UUID reservationB;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO tenants (id, name) VALUES (?, ?), (?, ?)",
            TENANT_A, "Entreprise A", TENANT_B, "Entreprise B");
        testUsers = new TestUsers(userRepository, jdbc, passwordEncoder);
        TestUsers testUsersA = testUsers.inTenant(TENANT_A);
        TestUsers testUsersB = testUsers.inTenant(TENANT_B);

        adminA = testUsersA.createWithoutPassword("admin@a.test", User.Role.ADMIN);
        agentA = testUsersA.createWithoutPassword("agent@a.test", User.Role.AGENT);
        driverA = testUsersA.createWithoutPassword("driver@a.test", User.Role.DRIVER);
        disabledA = testUsersA.createWithoutPassword("disabled@a.test", User.Role.DRIVER);
        jdbc.update("UPDATE users SET status = 'DISABLED', is_active = FALSE WHERE id = ?", disabledA.getId());
        vehicleA = insertVehicle(TENANT_A, "AA-111-AA");
        reservationA = insertReservation(TENANT_A, vehicleA, driverA, agentA, T0.plusDays(10), T0.plusDays(11));
        notificationA = jdbc.queryForObject(
            "INSERT INTO notifications (tenant_id, user_id, reservation_id, type) "
                + "VALUES (?, ?, ?, 'NEW_RESERVATION') RETURNING id",
            UUID.class, TENANT_A, adminA.getId(), reservationA);

        adminB = testUsersB.createWithoutPassword("admin@b.test", User.Role.ADMIN);
        agentB = testUsersB.createWithoutPassword("agent@b.test", User.Role.AGENT);
        driverB = testUsersB.createWithoutPassword("driver@b.test", User.Role.DRIVER);
        vehicleB = insertVehicle(TENANT_B, "BB-222-BB");
        reservationB = insertReservation(TENANT_B, vehicleB, driverB, agentB, T0.plusDays(10), T0.plusDays(11));
    }

    // --- Lecture : les listes ne contiennent que les données du tenant de l'utilisateur ---

    @Test
    void vehicleListShowsOnlyOwnTenant() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(vehicleB.toString()));

        mockMvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(vehicleA.toString()));
    }

    @Test
    void reservationListShowsOnlyOwnTenant() throws Exception {
        mockMvc.perform(get("/api/v1/reservations").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(reservationB.toString()));

        mockMvc.perform(get("/api/v1/reservations").header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(reservationA.toString()));
    }

    @Test
    void userListShowsOnlyOwnTenant() throws Exception {
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.content[*].email", everyItem(endsWith("@b.test"))));
    }

    @Test
    void driverListShowsOnlyOwnTenant() throws Exception {
        mockMvc.perform(get("/api/v1/users/drivers").header(HttpHeaders.AUTHORIZATION, bearer(agentB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(driverB.getId().toString()));
    }

    @Test
    void dashboardCountsOnlyOwnTenant() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.latestReservations.length()").value(1))
            .andExpect(jsonPath("$.latestReservations[0].id").value(reservationB.toString()))
            .andExpect(jsonPath("$.latestDrivers.length()").value(1))
            .andExpect(jsonPath("$.latestDrivers[0].id").value(driverB.getId().toString()))
            .andExpect(jsonPath("$.reservationsByAgent.length()").value(1))
            .andExpect(jsonPath("$.reservationsByAgent[0].count").value(1));
    }

    @Test
    void dashboardMaintenanceAndAgentStatsStayInOwnTenant() throws Exception {
        jdbc.update("UPDATE vehicles SET status = 'MAINTENANCE' WHERE id = ?", vehicleA);

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vehiclesInMaintenance.length()").value(0));

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vehiclesInMaintenance.length()").value(1));

        // Réservation de A attribuée à agentB (incohérente, insérée en SQL : la base ne l'empêche pas encore,
        // voir la checklist) : elle ne doit pas compter dans les statistiques de agentB
        insertReservation(TENANT_A, vehicleA, driverA, agentB, T0.plusDays(30), T0.plusDays(31));

        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(agentB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.myStats.totalReservations").value(1));
    }

    // --- Accès direct par id : une donnée d'un autre tenant répond 404, et rien ne change en base ---

    // Requêtes de B sur les données de A ; le contrôle positif envoie les mêmes requêtes en tant que A
    // Dernière colonne : réponse attendue chez A (contrôle positif). reservationA est à venir :
    // ni remise (rendez-vous pas encore atteint) ni retour (pas remise) ne sont possibles, d'où 409
    private static final String CROSS_TENANT_REQUESTS = """
        GET    | /api/v1/vehicles/{vehicleA}                  |                          | 200
        PATCH  | /api/v1/vehicles/{vehicleA}                  | {"brand":"Pirate"}       | 200
        PATCH  | /api/v1/vehicles/{vehicleA}/status           | {"status":"MAINTENANCE"} | 200
        DELETE | /api/v1/vehicles/{vehicleA}                  |                          | 204
        GET    | /api/v1/reservations/{reservationA}          |                          | 200
        POST   | /api/v1/reservations/{reservationA}/return   | RETURN                   | 409
        GET    | /api/v1/users/{driverA}                      |                          | 200
        PATCH  | /api/v1/users/{driverA}                      | {"firstname":"Pirate"}   | 200
        DELETE | /api/v1/users/{driverA}                      |                          | 204
        PATCH  | /api/v1/users/{disabledA}/reactivate         |                          | 200
        PATCH  | /api/v1/notifications/{notificationA}/read   |                          | 204
        PATCH  | /api/v1/reservations/{reservationA}          | {"notesBefore":"Pirate"} | 200
        POST   | /api/v1/reservations/{reservationA}/cancel   |                          | 200
        POST   | /api/v1/reservations/{reservationA}/handover | {"mileageStart":60000}   | 409
        """;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', textBlock = CROSS_TENANT_REQUESTS)
    void otherTenantResourceIsNotFound(String method, String path, String body, int expectedInOwnTenant) throws Exception {
        String before = snapshotOfTenantA();

        mockMvc.perform(requestAs(adminB, method, path, body)).andExpect(status().isNotFound());

        assertEquals(before, snapshotOfTenantA(), "Les données de l'entreprise A ne doivent pas changer");
    }

    // Contrôle positif : chez A, la même requête trouve la ressource et répond le statut attendu.
    // Le 404 ci-dessus vient donc bien du tenant
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', textBlock = CROSS_TENANT_REQUESTS)
    void sameRequestFindsTheResourceInOwnTenant(String method, String path, String body, int expectedInOwnTenant)
            throws Exception {
        mockMvc.perform(requestAs(adminA, method, path, body)).andExpect(status().is(expectedInOwnTenant));
    }

    // Même réponse qu'un id inexistant : B ne peut pas savoir que l'id existe chez A
    @Test
    void otherTenantIdAnswersLikeUnknownId() throws Exception {
        MvcResult otherTenant = mockMvc.perform(get("/api/v1/vehicles/" + vehicleA)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andReturn();
        MvcResult unknown = mockMvc.perform(get("/api/v1/vehicles/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andReturn();

        assertEquals(404, otherTenant.getResponse().getStatus());
        assertEquals(unknown.getResponse().getStatus(), otherTenant.getResponse().getStatus());
        assertEquals(unknown.getResponse().getContentAsString(), otherTenant.getResponse().getContentAsString());
    }

    // --- Création : la donnée créée appartient au tenant de son auteur ---

    @Test
    void createdVehicleBelongsToCreatorsTenant() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "plateNumber", "BB-333-BB", "brand", "Renault", "model", "Clio",
            "year", 2022, "mileageKM", 1000, "condition", "GOOD", "commissionedAt", "2022-01-01"));

        MvcResult result = mockMvc.perform(post("/api/v1/vehicles")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();

        assertEquals(TENANT_B, tenantOf("vehicles", idOf(result)));
    }

    @Test
    void driverReservationBelongsToCreatorsTenant() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(driverB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(vehicleB, Map.of())))
            .andExpect(status().isCreated())
            .andReturn();

        assertEquals(TENANT_B, tenantOf("reservations", idOf(result)));
    }

    @Test
    void createdUserBelongsToCreatorsTenant() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "firstname", "Bob", "lastname", "Martin", "email", "new@b.test",
            "password", "Password123!", "role", "DRIVER", "active", true));

        MvcResult result = mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn();

        assertEquals(TENANT_B, tenantOf("users", idOf(result)));
    }

    // Conducteur créé à la volée par l'agent (DriverAssignmentService) : dans le tenant de l'agent
    @Test
    void driverCreatedWithReservationBelongsToAgentsTenant() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(agentB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(vehicleB, Map.of(
                    "driverFirstName", "Léa", "driverLastName", "Petit", "driverEmail", "lea@b.test"))))
            .andExpect(status().isCreated());

        assertEquals(TENANT_B, jdbc.queryForObject(
            "SELECT tenant_id FROM users WHERE email = 'lea@b.test'", UUID.class));
    }

    @Test
    void reservingAnotherTenantsVehicleIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(driverB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(vehicleA, Map.of())))
            .andExpect(status().isNotFound());

        assertEquals(1, countReservationsOf(vehicleA));
    }

    @Test
    void agentCannotAssignAnotherTenantsDriver() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(agentB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(vehicleB, Map.of("driverId", driverA.getId().toString()))))
            .andExpect(status().isNotFound());

        assertEquals(1, countReservationsOf(vehicleB));
    }

    // Les emails sont uniques pour toute l'application : celui d'un compte de A est refusé chez B (409, pas 500)
    @Test
    void staffEmailOfAnotherTenantIsRefused() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "firstname", "Bob", "lastname", "Pirate", "email", "driver@a.test",
            "password", "Password123!", "role", "DRIVER", "active", true));

        mockMvc.perform(post("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());

        assertEquals(List.of(TENANT_A), jdbc.queryForList(
            "SELECT tenant_id FROM users WHERE email = 'driver@a.test'", UUID.class));
    }

    @Test
    void newDriverEmailOfAnotherTenantIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(agentB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(vehicleB, Map.of(
                    "driverFirstName", "Bob", "driverLastName", "Pirate", "driverEmail", "driver@a.test"))))
            .andExpect(status().isConflict());

        assertEquals(1, countReservationsOf(vehicleB));
    }

    // --- Notifications : seuls les agents et administrateurs du tenant de la réservation sont prévenus ---

    @Test
    void newReservationNotifiesOnlyOwnTenantStaff() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(driverB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationBody(vehicleB, Map.of())))
            .andExpect(status().isCreated())
            .andReturn();
        UUID created = idOf(result);

        assertEquals(Set.of(adminB.getId(), agentB.getId()), notifiedUsersOf(created));
        assertEquals(List.of(TENANT_B), jdbc.queryForList(
            "SELECT DISTINCT tenant_id FROM notifications WHERE reservation_id = ?", UUID.class, created));
    }

    @Test
    void notificationListShowsOnlyOwn() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    // Le traitement planifié tourne sans utilisateur connecté : il traite chaque tenant séparément
    @Test
    void endingSoonNotificationsStayInReservationTenant() {
        UUID endingA = insertReservation(TENANT_A, vehicleA, driverA, agentA, T0.minusDays(1), T0.plusHours(24));
        UUID endingB = insertReservation(TENANT_B, vehicleB, driverB, agentB, T0.minusDays(1), T0.plusHours(24));
        jdbc.update("UPDATE reservations SET handed_over_at = start_date WHERE id IN (?, ?)", endingA, endingB);

        notificationService.checkEndingSoon();

        assertEquals(Set.of(adminA.getId(), agentA.getId()), notifiedUsersOf(endingA));
        assertEquals(Set.of(adminB.getId(), agentB.getId()), notifiedUsersOf(endingB));
        assertEquals(List.of(TENANT_A), jdbc.queryForList(
            "SELECT DISTINCT tenant_id FROM notifications WHERE reservation_id = ?", UUID.class, endingA));
        assertEquals(List.of(TENANT_B), jdbc.queryForList(
            "SELECT DISTINCT tenant_id FROM notifications WHERE reservation_id = ?", UUID.class, endingB));
    }

    // --- Connexion et inscription : hors de tout tenant, puis rattachement ---

    // La connexion cherche l'email dans tous les tenants ; la session obtenue est ensuite limitée à B
    @Test
    void loginWorksForAnotherTenantThenSeesOnlyItsData() throws Exception {
        testUsers.inTenant(TENANT_B).create("login@b.test", "Password123!", User.Role.ADMIN);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@b.test\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("access_token").asString();

        mockMvc.perform(get("/api/v1/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(vehicleB.toString()));
    }

    // Un en-tête Bearer envoyé sur les endpoints publics ne doit pas les limiter au tenant du token
    @Test
    void publicAuthEndpointsIgnoreBearerOfAnotherTenant() throws Exception {
        testUsers.inTenant(TENANT_A).create("login@a.test", "Password123!", User.Role.ADMIN);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"login@a.test\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String refreshToken = objectMapper.readTree(login.getResponse().getContentAsString())
            .get("refresh_token").asString();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refresh_token", refreshToken))))
            .andExpect(status().isOk());

        var register = TestRequests.validRegisterRequest();
        mockMvc.perform(post("/api/v1/auth/register")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminB))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isAccepted());
        assertEquals(Tenants.DEFAULT_ID, jdbc.queryForObject(
            "SELECT tenant_id FROM users WHERE email = ?", UUID.class, User.normalizeEmail(register.email())));
    }

    // Test de l'inscription hors de tout tenant
    @Test
    void registrationJoinsDefaultTenant() throws Exception {
        var request = TestRequests.validRegisterRequest();
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isAccepted());

        assertEquals(Tenants.DEFAULT_ID, jdbc.queryForObject(
            "SELECT tenant_id FROM users WHERE email = ?", UUID.class, User.normalizeEmail(request.email())));
    }

    // Sans utilisateur connecté (traitement planifié, inscription), le code doit fixer le tenant lui-même :
    // un oubli est refusé par la base au lieu de ranger la donnée dans un tenant au hasard
    @Test
    void insertWithoutTenantOutsideUserContextIsRefused() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber("ZZ-999-ZZ");
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");

        var error = assertThrows(DataIntegrityViolationException.class, () -> vehicleRepository.saveAndFlush(vehicle));
        String cause = error.getMostSpecificCause().getMessage();
        assertTrue(cause.contains("fk_vehicles_tenant_id"), "Refus attendu sur la clé étrangère du tenant : " + cause);
    }

    // --- Outils ---

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private UUID insertVehicle(UUID tenantId, String plate) {
        return jdbc.queryForObject(
            "INSERT INTO vehicles (tenant_id, plate_number, brand, model, mileage_km, condition, status) "
                + "VALUES (?, ?, 'Toyota', 'Yaris', ?, 'GOOD', 'AVAILABLE') RETURNING id",
            UUID.class, tenantId, plate, MILEAGE);
    }

    private UUID insertReservation(UUID tenantId, UUID vehicleId, User driver, User agent,
                                   OffsetDateTime start, OffsetDateTime end) {
        return jdbc.queryForObject(
            "INSERT INTO reservations (tenant_id, vehicle_id, driver_id, agent_id, start_date, end_date, "
                + "condition_before, mileage_start) VALUES (?, ?, ?, ?, ?, ?, 'GOOD', ?) RETURNING id",
            UUID.class, tenantId, vehicleId, driver.getId(), agent.getId(), start, end, MILEAGE);
    }

    private String reservationBody(UUID vehicleId, Map<String, Object> extra) throws Exception {
        var body = new java.util.HashMap<String, Object>(extra);
        body.put("vehicleId", vehicleId.toString());
        body.put("startDate", T0.plusDays(20).toString());
        body.put("endDate", T0.plusDays(21).toString());
        return objectMapper.writeValueAsString(body);
    }

    private MockHttpServletRequestBuilder requestAs(User actor, String method, String path, String body) throws Exception {
        var request = request(HttpMethod.valueOf(method), resolve(path))
            .header(HttpHeaders.AUTHORIZATION, bearer(actor));
        if (body != null) {
            String json = "RETURN".equals(body)
                ? objectMapper.writeValueAsString(TestRequests.validReturnVehicleRequest())
                : body;
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request;
    }

    private String resolve(String path) {
        return path
            .replace("{vehicleA}", vehicleA.toString())
            .replace("{reservationA}", reservationA.toString())
            .replace("{driverA}", driverA.getId().toString())
            .replace("{disabledA}", disabledA.getId().toString())
            .replace("{notificationA}", notificationA.toString());
    }

    // État des lignes de A qu'une requête de B pourrait modifier
    private String snapshotOfTenantA() {
        return jdbc.queryForList(
                "SELECT brand, status, deleted_at, mileage_km FROM vehicles WHERE id = ?", vehicleA).toString()
            + jdbc.queryForList(
                "SELECT returned_at, mileage_end, notes_before, cancelled_at, handed_over_at, mileage_start FROM reservations WHERE id = ?", reservationA).toString()
            + jdbc.queryForList(
                "SELECT is_read FROM notifications WHERE id = ?", notificationA).toString()
            + jdbc.queryForList(
                "SELECT id, first_name, is_active FROM users WHERE tenant_id = ? ORDER BY id", TENANT_A).toString();
    }

    private UUID idOf(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    private UUID tenantOf(String table, UUID id) {
        return jdbc.queryForObject("SELECT tenant_id FROM " + table + " WHERE id = ?", UUID.class, id);
    }

    private int countReservationsOf(UUID vehicleId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE vehicle_id = ?", Integer.class, vehicleId);
    }

    // Comparé en ensemble (l'ordre des UUID diffère entre Java et PostgreSQL) ; un doublon est vérifié avant
    private Set<UUID> notifiedUsersOf(UUID reservationId) {
        List<UUID> users = jdbc.queryForList(
            "SELECT user_id FROM notifications WHERE reservation_id = ?", UUID.class, reservationId);
        assertEquals(Set.copyOf(users).size(), users.size(), "Utilisateur prévenu plusieurs fois");
        return Set.copyOf(users);
    }
}
