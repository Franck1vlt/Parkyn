package app.parkyn.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import app.parkyn.model.Notification.NotificationType;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.UserRepository;
import app.parkyn.repository.VehicleRepository;
import app.parkyn.security.authorization.ReservationScope;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final DriverAssignmentService driverAssignmentService;
    private final NotificationService notificationService;
    private final ReservationScope reservationScope;

    @Transactional
    public Page<ReservationResponse> getReservations(User currentUser, String status, UUID vehicleId, Pageable pageable) {
        Specification<Reservation> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if ("ACTIVE".equals(status)) {
                predicates.add(cb.isNull(root.get("returnedAt")));
            } else if ("RETURNED".equals(status)) {
                predicates.add(cb.isNotNull(root.get("returnedAt")));
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicle").get("id"), vehicleId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        spec = spec.and(reservationScope.readableBy(currentUser));
        return reservationRepository.findAll(spec, pageable).map(ReservationResponse::from);
    }

    @Transactional
    public ReservationResponse addReservation(User currentUser, ReservationRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
            .filter(v -> v.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));

        validateCreate(request, vehicle);
        User driver = driverAssignmentService.getOrCreateDriver(request);

        User agent;
        if (request.agentId() != null) {
            agent = userRepository.findById(request.agentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent introuvable"));
        } else {
            agent = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        }

        Reservation reservation = new Reservation();
        reservation.setVehicle(vehicle);
        reservation.setDriver(driver);
        reservation.setAgent(agent);
        reservation.setConditionBefore(request.conditionBefore());
        reservation.setMileageStart(request.mileageStart());
        reservation.setNotesBefore(request.notesBefore());
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setCreatedBy(currentUser);
        reservation.setUpdatedBy(currentUser);
        vehicle.setStatus(Vehicle.VehicleStatus.RESERVED);
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        notificationService.createForAllAdminsAndAgents(NotificationType.NEW_RESERVATION, saved);
        return ReservationResponse.from(saved);
    }

    @Transactional
    public ReservationResponse getReservationById(User currentUser, UUID id) {
        return ReservationResponse.from(findOne(currentUser, id));
    }

    private Reservation findOne(User currentUser, UUID id) {
        Specification<Reservation> byId = (root, query, cb) -> cb.equal(root.get("id"), id);
        return reservationRepository.findOne(byId.and(reservationScope.readableBy(currentUser)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    @Transactional
    public ReservationResponse returnVehicle(User currentUser, UUID id, ReturnVehicleRequest request) {
        Reservation reservation = findReservationOrThrow(id);

        validateReturn(reservation, request);

        reservation.setReturnedAt(OffsetDateTime.now());
        reservation.setConditionAfter(request.conditionAfter());
        reservation.setMileageEnd(request.mileageEnd());
        reservation.setDamageReported(request.damageReported());
        reservation.setDamageDetails(request.damageDetails());
        reservation.setNotesAfter(request.notesAfter());
        reservation.setUpdatedBy(currentUser);

        Vehicle vehicle = reservation.getVehicle();
        vehicle.setStatus(Vehicle.VehicleStatus.AVAILABLE);
        vehicle.setMileageKM(request.mileageEnd());

        return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
    }

    private Reservation findReservationOrThrow(UUID id) {
        return reservationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    private void validateCreate(ReservationRequest request, Vehicle vehicle){
        // Le véhicule est disponible ?
        if (vehicle.getStatus() != Vehicle.VehicleStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Véhicule non disponible");
        }

        // Les dates sont cohérentes ?
        if (!request.startDate().isBefore(request.endDate())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }

        // La réservation n'est pas dans le passé
        if (request.startDate().isBefore(OffsetDateTime.now())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be in the past");
        }

        // Le kilométrage de départ est cohérent avec le véhicule
        if (request.mileageStart() < vehicle.getMileageKM()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mileageStart cannot be less than vehicle current mileage");
        }

        // Pas de conflit de réservation
        if (reservationRepository.hasConflict(vehicle.getId(), request.startDate(), request.endDate())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle already reserved for this period");
        }
    }

    private void validateReturn(Reservation reservation, ReturnVehicleRequest request){
        // La réservation n'est pas déjà retournée
        if (reservation.getReturnedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "vehicle already returned");
        }

        // Le kilométrage de fin est cohérent avec le véhicule
        if (request.mileageEnd() < reservation.getMileageStart()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mileageEnd cannot be less than mileageStart");
        }

        // Les dommages sont signalés, les détails doivent être fournis
        if (request.damageReported() && (request.damageDetails() == null || request.damageDetails().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "damageDetails must be provided if damageReported is true");
        }
    }
}
