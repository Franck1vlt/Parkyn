package app.parkyn.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import app.parkyn.IntegrationTest;
import app.parkyn.TestUsers;
import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.model.Vehicle.VehicleCondition;
import app.parkyn.model.Vehicle.VehicleStatus;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@IntegrationTest
public class ReservationScopeTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ObjectMapper objectMapper;

    private static final String TEST_DRIVER_A = "driverA@parkyn.app";
    private static final String TEST_DRIVER_B = "driverB@parkyn.app";
    private static final String TEST_AGENT = "agent@parkyn.app";
    private static final String TEST_ADMIN = "admin@parkyn.app";
    private static final String PASSWORD = "Password123!";
    private static final String DRIVER_DESIGNATION_ERROR = "Un conducteur ne peut pas désigner un autre conducteur";
    // Dates relatives à maintenant : le test ne devient jamais "dans le passé"
    private static final OffsetDateTime T0 = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    // Créneau libre pour les nouvelles réservations : après r1, r2 et r3 (T0+1 à T0+6 jours)
    private static final OffsetDateTime NEW_START = T0.plusDays(7);
    private static final OffsetDateTime NEW_END = T0.plusDays(8);
    private String driverAId;
    private String agentId;
    private String adminId;
    private Reservation r2;

    @BeforeEach
    void setUp() {
        TestUsers testUsers = new TestUsers(userRepository, jdbcTemplate, passwordEncoder);

        // Users
        User driverA = testUsers.create(TEST_DRIVER_A, PASSWORD, User.Role.DRIVER);
        User driverB = testUsers.create(TEST_DRIVER_B, PASSWORD, User.Role.DRIVER);
        User agent = testUsers.create(TEST_AGENT, PASSWORD, User.Role.AGENT);
        User admin = testUsers.create(TEST_ADMIN, PASSWORD, User.Role.ADMIN);
        driverAId = driverA.getId().toString();
        agentId = agent.getId().toString();
        adminId = admin.getId().toString();

        // Véhicule
        Vehicle car = new Vehicle();
        car.setPlateNumber("AA-123-BB");
        car.setBrand("Toyota");
        car.setModel("Yaris");
        car.setYear(2009);
        car.setMileageKM(135000);
        car.setCondition(VehicleCondition.GOOD);
        car.setStatus(VehicleStatus.AVAILABLE);
        car.setCommissionedAt(null);
        car.setDeletedAt(null);
        car.setCreatedAt(T0);
        vehicleRepository.saveAndFlush(car);

        // Réservation
        Reservation r1 = new Reservation();
        r1.setVehicle(car);
        r1.setDriver(driverA);
        r1.setAgent(agent);
        r1.setStartDate(T0.plusDays(1));
        r1.setEndDate(T0.plusDays(2));
        r1.setConditionBefore(Reservation.Condition.FAIR);
        r1.setMileageStart(135000);
        r1.setCreatedAt(T0);
        reservationRepository.saveAndFlush(r1);

        // État de départ FAIR, différent de l'état du véhicule (GOOD) et du retour (POOR)
        r2 = new Reservation();
        r2.setVehicle(car);
        r2.setDriver(driverB);
        r2.setAgent(agent);
        r2.setStartDate(T0.plusDays(3));
        r2.setEndDate(T0.plusDays(4));
        r2.setConditionBefore(Reservation.Condition.FAIR);
        r2.setMileageStart(135000);
        r2.setCreatedAt(T0);
        reservationRepository.saveAndFlush(r2);

        // Réservation sans agent référent (créée par un conducteur) : l'agent n'en est pas l'agent
        Reservation r3 = new Reservation();
        r3.setVehicle(car);
        r3.setDriver(driverB);
        r3.setAgent(null);
        r3.setStartDate(T0.plusDays(5));
        r3.setEndDate(T0.plusDays(6));
        r3.setConditionBefore(Reservation.Condition.GOOD);
        r3.setMileageStart(135000);
        r3.setCreatedAt(T0);
        reservationRepository.saveAndFlush(r3);
    }

    // Driver A liste les réservations : il ne voit que la sienne
    @Test
    void driverAOnlySeesTheirReservation() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].driverId").value(driverAId.toString()));
    }

    // Driver A demande la réservation de Driver B : il reçoit 404
    @Test
    void driverACannotAccessDriverBReservation() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/" + r2.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // Driver A demande un id inexistant : il reçoit 404
    @Test
    void driverAReceives404ForNonExistentReservation() throws Exception {
        MvcResult nonExistentReservation = mockMvc.perform(
            get("/api/v1/reservations/00000000-0000-0000-0000-000000000000")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
            .andReturn();

        MvcResult driverBReservation = mockMvc.perform(
            get("/api/v1/reservations/" + r2.getId())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD)))
            .andReturn();

        assertEquals(404, nonExistentReservation.getResponse().getStatus());
        assertEquals(nonExistentReservation.getResponse().getStatus(),
            driverBReservation.getResponse().getStatus());
        assertEquals(nonExistentReservation.getResponse().getContentAsString(),
            driverBReservation.getResponse().getContentAsString());
    }

    // Agent liste les réservations : il voit les trois, y compris r3 dont il n'est pas l'agent
    @Test
    void agentSeesAllReservations() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_AGENT, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    // Admin liste les réservations : il voit les trois sans en être ni l'agent ni le conducteur
    @Test
    void adminSeesAllReservations() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_ADMIN, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    // Driver A crée sa réservation : il reçoit 201
    @Test
    void driverACreatesReservation() throws Exception {
        String requestBody = """
            {
                "vehicleId": "%s",
                "startDate": "%s",
                "endDate": "%s"
            }
        """.formatted(r2.getVehicle().getId(), NEW_START, NEW_END);

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverId").value(driverAId.toString()))
                .andExpect(jsonPath("$.agentId").doesNotExist())
                .andExpect(jsonPath("$.mileageStart").value(r2.getVehicle().getMileageKM()))
                .andExpect(jsonPath("$.conditionBefore").value(r2.getVehicle().getCondition().toString()));
    }

    // Driver A envoie un autre driverId : il reçoit 400
    @Test
    void driverACannotCreateReservationForAnotherDriver() throws Exception {
        String requestBody = """
            {
                "vehicleId": "%s",
                "driverId": "%s",
                "startDate": "%s",
                "endDate": "%s"
            }
        """.formatted(r2.getVehicle().getId(), r2.getDriver().getId(), NEW_START, NEW_END);

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(DRIVER_DESIGNATION_ERROR));
    }

    // Driver A envoie un seul champ de conducteur (email, prénom ou nom) : il reçoit 400
    @ParameterizedTest(name = "{0} seul : 400")
    @CsvSource({
        "driverEmail, intrus@parkyn.app",
        "driverFirstName, Jean",
        "driverLastName, Dupont"
    })
    void driverACannotDesignateADriverWithASingleField(String field, String value) throws Exception {
        String requestBody = """
            {
                "vehicleId": "%s",
                "%s": "%s",
                "startDate": "%s",
                "endDate": "%s"
            }
        """.formatted(r2.getVehicle().getId(), field, value, NEW_START, NEW_END);

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_A, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(DRIVER_DESIGNATION_ERROR));
    }

    // Agent crée pour driver B : il reçoit 201
    // conditionBefore, mileageStart et agentId de la requête sont faux : ils doivent être ignorés
    @Test
    void agentCreatesReservationForDriverB() throws Exception {
        String requestBody = """
            {
                "vehicleId": "%s",
                "driverId": "%s",
                "agentId": "%s",
                "conditionBefore": "EXCELLENT",
                "mileageStart": 999999,
                "startDate": "%s",
                "endDate": "%s"
            }
        """.formatted(r2.getVehicle().getId(), r2.getDriver().getId(), adminId, NEW_START, NEW_END);

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_AGENT, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverId").value(r2.getDriver().getId().toString()))
                .andExpect(jsonPath("$.agentId").value(agentId))
                .andExpect(jsonPath("$.mileageStart").value(135000))
                .andExpect(jsonPath("$.conditionBefore").value(VehicleCondition.GOOD.toString()));
    }

    // Admin crée pour driver B : il reçoit 201 et devient l'agent référent
    @Test
    void adminCreatesReservationForDriverB() throws Exception {
        String requestBody = """
            {
                "vehicleId": "%s",
                "driverId": "%s",
                "startDate": "%s",
                "endDate": "%s"
            }
        """.formatted(r2.getVehicle().getId(), r2.getDriver().getId(), NEW_START, NEW_END);

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_ADMIN, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverId").value(r2.getDriver().getId().toString()))
                .andExpect(jsonPath("$.agentId").value(adminId));
    }

    // Véhicule sans état connu : la réservation par un agent est refusée (409)
    @Test
    void agentCannotReserveVehicleWithUnknownCondition() throws Exception {
        Vehicle noConditionCar = new Vehicle();
        noConditionCar.setPlateNumber("CC-456-DD");
        noConditionCar.setBrand("Peugeot");
        noConditionCar.setModel("208");
        noConditionCar.setYear(2018);
        noConditionCar.setMileageKM(60000);
        noConditionCar.setCondition(null);
        noConditionCar.setStatus(VehicleStatus.AVAILABLE);
        noConditionCar.setCreatedAt(T0);
        vehicleRepository.saveAndFlush(noConditionCar);

        String requestBody = """
            {
                "vehicleId": "%s",
                "driverId": "%s",
                "startDate": "%s",
                "endDate": "%s"
            }
        """.formatted(noConditionCar.getId(), r2.getDriver().getId(), NEW_START, NEW_END);

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_AGENT, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(content().string("État du véhicule inconnu : renseignez-le avant de le réserver"));
    }

    // Retour puis nouvelle réservation : l'état de départ vient du retour (POOR),
    // pas de l'état initial du véhicule (GOOD) ni de celui de r2 au départ (FAIR)
    @Test
    void returnThenNewReservation() throws Exception {
        Vehicle vehicleBeforeReturn = vehicleRepository.findById(r2.getVehicle().getId()).orElseThrow();
        assertEquals(VehicleCondition.GOOD, vehicleBeforeReturn.getCondition());
        assertEquals(Reservation.Condition.FAIR, r2.getConditionBefore());

        String returnRequestBody = """
            {
                "conditionAfter": "POOR",
                "mileageEnd": 140000,
                "damageReported": true,
                "damageDetails": "Rayure sur la portière",
                "notesAfter": "Le véhicule a été rendu avec un peu de retard"
            }
        """;
        // Le retour est toujours fait par un agent (reservation:return:all)
        mockMvc.perform(post("/api/v1/reservations/{id}/return", r2.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_AGENT, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(returnRequestBody))
                .andExpect(status().isOk());

        Vehicle returnedVehicle = vehicleRepository.findById(r2.getVehicle().getId()).orElseThrow();
        assertEquals(VehicleCondition.POOR, returnedVehicle.getCondition());
        assertEquals(140000, returnedVehicle.getMileageKM());

        String newReservationBody = """
            {
                "vehicleId": "%s",
                "startDate": "%s",
                "endDate": "%s"
            }
        """.formatted(returnedVehicle.getId(), NEW_START, NEW_END);

        mockMvc.perform(post("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login(TEST_DRIVER_B, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(newReservationBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conditionBefore").value(VehicleCondition.POOR.toString()))
                .andExpect(jsonPath("$.mileageStart").value(140000));
    }

    // --- Aides ---

    // Se connecte via /auth/login et renvoie l'access token
    private String login(String email, String password) throws Exception {
        // même modèle que getAccessToken(...) dans tes tests de contrôleurs
        LoginRequest request = new LoginRequest(email, password);
        String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, LoginResponse.class).accessToken();
    }

}
