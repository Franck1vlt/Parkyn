package app.parkyn;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.model.Vehicle.VehicleCondition;
import app.parkyn.model.Vehicle.VehicleStatus;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;

@RepositoryTest
class ReservationRepositoryTest {

    @Autowired ReservationRepository reservationRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired UserRepository userRepository;

    // Date fixe pour tous les tests
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-02T10:00:00Z");

    private User saveUser(String email, User.Role role) {
        User user = new User();
        user.setFirstName("Jean");
        user.setLastName("Dupont");
        user.setEmail(email);
        user.setHashedPwd("hash");
        return userRepository.saveAndFlush(user);
    }

    private Vehicle saveVehicle(String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(plate);
        vehicle.setBrand("Toyota");
        vehicle.setModel("Yaris");
        vehicle.setYear(2009);
        vehicle.setMileageKM(135000);
        vehicle.setCondition(VehicleCondition.GOOD);
        vehicle.setStatus(VehicleStatus.AVAILABLE);
        vehicle.setCommissionedAt(null);
        vehicle.setDeletedAt(null);
        vehicle.setCreatedAt(T0);
        return vehicleRepository.saveAndFlush(vehicle);
    }

    private Reservation saveReservation(Vehicle vehicle, User driver, User agent,
                                        OffsetDateTime start, OffsetDateTime end) {
        Reservation reservation = new Reservation();
        reservation.setVehicle(vehicle);
        reservation.setDriver(driver);
        reservation.setAgent(agent);
        reservation.setStartDate(start);
        reservation.setEndDate(end);
        reservation.setConditionBefore(Reservation.Condition.FAIR);
        reservation.setMileageStart(135000);
        return reservationRepository.saveAndFlush(reservation);
    }

    @Test 
    void overlappingReservationIsAConflict() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        User agent = saveUser("agent@example.com", User.Role.AGENT);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        Reservation reservation = saveReservation(vehicle, driver, agent, start, end);
        OffsetDateTime newStart = T0.plusHours(1);
        OffsetDateTime newEnd = T0.plusHours(3);
        assertTrue(reservationRepository.hasConflict(vehicle.getId(), newStart, newEnd));
    }

    @Test 
    void includedReservationIsAConflict() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        User agent = saveUser("agent@example.com", User.Role.AGENT);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(3);
        Reservation reservation = saveReservation(vehicle, driver, agent, start, end);
        OffsetDateTime newStart = T0.plusHours(1);
        OffsetDateTime newEnd = T0.plusHours(2);
        assertTrue(reservationRepository.hasConflict(vehicle.getId(), newStart, newEnd));
    }

    @Test 
    void adjacentReservationsAreNotAConflict() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        User agent = saveUser("agent@example.com", User.Role.AGENT);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        Reservation reservation = saveReservation(vehicle, driver, agent, start, end);
        OffsetDateTime newStart = T0.plusHours(2);
        OffsetDateTime newEnd = T0.plusHours(4);
        assertFalse(reservationRepository.hasConflict(vehicle.getId(), newStart, newEnd));
    }

    @Test 
    void returnedReservationIsNotAConflict() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        User agent = saveUser("agent@example.com", User.Role.AGENT);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        Reservation reservation = saveReservation(vehicle, driver, agent, start, end);
        reservation.setReturnedAt(T0.plusHours(1));
        reservationRepository.saveAndFlush(reservation);
        OffsetDateTime newStart = T0.plusHours(1);
        OffsetDateTime newEnd = T0.plusHours(3);
        assertFalse(reservationRepository.hasConflict(vehicle.getId(), newStart, newEnd));
    }

    @Test 
    void samePeriodWithDifferentVehicleIsNotAConflict() {
        Vehicle vehicle1 = saveVehicle("ABC123");
        Vehicle vehicle2 = saveVehicle("XYZ789");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        User agent = saveUser("agent@example.com", User.Role.AGENT);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        saveReservation(vehicle1, driver, agent, start, end);
        assertFalse(reservationRepository.hasConflict(vehicle2.getId(), start, end));
    }

    @Test
    void findAllWithNoAgent() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        saveReservation(vehicle, driver, null, start, end);
        assertEquals(1, reservationRepository
            .findAll((root, query, cb) -> null, PageRequest.of(0, 10))
            .getContent()
            .size());
    }

    @Test
    void findLatestDriversWithMultipleReservations() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        User agent = saveUser("agent@example.com", User.Role.AGENT);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        saveReservation(vehicle, driver, agent, start, end);
        saveReservation(vehicle, driver, agent, start.plusHours(3), end.plusHours(3));
        assertEquals(1, reservationRepository.findLatestDrivers(agent.getId()).size());
    }

    @Test 
    void findLatestDriversWithNullAgentId() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver1 = saveUser("driver1@example.com", User.Role.DRIVER);
        User driver2 = saveUser("driver2@example.com", User.Role.DRIVER);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        saveReservation(vehicle, driver1, null, start, end);
        saveReservation(vehicle, driver2, null, start.plusHours(3), end.plusHours(3));
        assertEquals(2, reservationRepository.findLatestDrivers(null).size());
    }

    @Test
    void countReservationsByAgent() {
        Vehicle vehicle = saveVehicle("ABC123");
        User driver = saveUser("driver@example.com", User.Role.DRIVER);
        User agent = saveUser("agent@example.com", User.Role.AGENT);
        OffsetDateTime start = T0;
        OffsetDateTime end = T0.plusHours(2);
        saveReservation(vehicle, driver, agent, start, end);
        saveReservation(vehicle, driver, null, start.plusHours(3), end.plusHours(3));
        var result = reservationRepository.countReservationsByAgent();
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0)[2]);
    }
}