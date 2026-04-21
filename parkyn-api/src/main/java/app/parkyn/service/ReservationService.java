package app.parkyn.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.ReservationResponse;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    @Transactional
    public Page<ReservationResponse> getReservations(Reservation.Condition status, Pageable pageable) {
        Specification<Reservation> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) {
                predicates.add(cb.equal(root.get("conditionBefore"), status));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return reservationRepository.findAll(spec, pageable).map(ReservationResponse::from);
    }

    @Transactional
    public ReservationResponse addReservation(ReservationRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
            .filter(v -> v.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));

        if (vehicle.getStatus() != Vehicle.VehicleStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Véhicule non disponible");
        }        

        User driver = userRepository.findById(request.driverId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conducteur introuvable"));

        User agent = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Reservation reservation = new Reservation();
        reservation.setVehicle(vehicle);
        reservation.setDriver(driver);
        reservation.setAgent(agent);
        reservation.setConditionBefore(request.conditionBefore());
        reservation.setMileageStart(request.mileageStart());
        reservation.setNotesBefore(request.notesBefore());
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        vehicle.setStatus(Vehicle.VehicleStatus.RESERVED);
        return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
    }

    @Transactional
    public ReservationResponse getReservationById(UUID id) {
        return ReservationResponse.from(findActiveReservation(id));
    }

    @Transactional
    public ReservationResponse returnVehicle(UUID id, ReturnVehicleRequest request) {
        Reservation reservation = findActiveReservation(id);

        if (reservation.getReturnedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Véhicule déjà retourné");
        }

        reservation.setReturnedAt(OffsetDateTime.now());
        reservation.setConditionAfter(request.conditionAfter());
        reservation.setMileageEnd(request.mileageEnd());
        reservation.setDamageReported(request.damageReported());
        reservation.setDamageDetails(request.damageDetails());
        reservation.setNotesAfter(request.notesAfter());

        Vehicle vehicle = reservation.getVehicle();
        vehicle.setStatus(Vehicle.VehicleStatus.AVAILABLE);
        vehicle.setMileageKM(request.mileageEnd());

        return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
    }

    private Reservation findActiveReservation(UUID id) {
        return reservationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reservation not found"));
    }


}
