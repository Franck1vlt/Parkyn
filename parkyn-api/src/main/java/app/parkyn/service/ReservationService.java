package app.parkyn.service;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.ReservationResponse;
import app.parkyn.dto.ReservationUpdateRequest;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.model.Notification.NotificationType;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.model.Vehicle;
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

    private static final Set<String> STATUS_FILTERS =
        Set.of("ACTIVE", "UPCOMING", "IN_PROGRESS", "RETURNED", "CANCELLED");

    private static final String SLOT_TAKEN =
        "Véhicule déjà réservé sur ce créneau : choisissez un autre véhicule ou une autre plage horaire";

    @Transactional
    public Page<ReservationResponse> getReservations(User currentUser, String status, UUID vehicleId, Pageable pageable) {
        if (status != null && !STATUS_FILTERS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu : " + status);
        }
        String filter = status;
        OffsetDateTime now = OffsetDateTime.now();
        Specification<Reservation> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            // Statuts calculés (Reservation.statusAt) ; ACTIVE = ni retournée ni annulée (frontend actuel)
            var returnedAt = root.<OffsetDateTime>get("returnedAt");
            var cancelledAt = root.<OffsetDateTime>get("cancelledAt");
            var startDate = root.<OffsetDateTime>get("startDate");
            switch (filter) {
                case null -> { }
                case "ACTIVE" -> predicates.add(cb.and(cb.isNull(returnedAt), cb.isNull(cancelledAt)));
                case "UPCOMING" -> predicates.add(cb.and(cb.isNull(returnedAt), cb.isNull(cancelledAt), cb.greaterThan(startDate, now)));
                case "IN_PROGRESS" -> predicates.add(cb.and(cb.isNull(returnedAt), cb.isNull(cancelledAt), cb.lessThanOrEqualTo(startDate, now)));
                case "RETURNED" -> predicates.add(cb.and(cb.isNotNull(returnedAt), cb.isNull(cancelledAt)));
                case "CANCELLED" -> predicates.add(cb.isNotNull(cancelledAt));
                default -> throw new IllegalStateException(filter);
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicle").get("id"), vehicleId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        spec = spec.and(reservationScope.readableBy(currentUser));
        // Même instant pour le filtre et le statut affiché
        return reservationRepository.findAll(spec, pageable).map(reservation -> ReservationResponse.from(reservation, now));
    }

    @Transactional
    public ReservationResponse addReservation(User currentUser, ReservationRequest request) {
        // Verrou sur le véhicule : deux demandes simultanées sont traitées l'une après l'autre,
        // la seconde voit la réservation de la première et reçoit le conflit
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(request.vehicleId())
            .filter(v -> v.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));

        validateCreate(request, vehicle);

        // Qui est le conducteur, qui est l'agent référent : décidé par les permissions,
        // jamais par le corps de la requête (identité lue dans le token).
        User driver;
        User agent;
        if (reservationScope.canCreateForAnyDriver(currentUser)) {
            // AGENT ou ADMIN : conducteur choisi (existant ou nouveau), il est l'agent référent
            driver = driverAssignmentService.getOrCreateDriver(request);
            agent = currentUser;
        } else {
            // DRIVER : il réserve pour lui-même, sans agent référent
            if (designatesADriver(request)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un conducteur ne peut pas désigner un autre conducteur");
            }
            driver = currentUser;
            agent = null;
        }

        Reservation reservation = new Reservation();
        reservation.setVehicle(vehicle);
        reservation.setDriver(driver);
        reservation.setAgent(agent);
        // État et kilométrage au départ : ceux du véhicule, mis à jour à chaque retour.
        // conditionBefore, mileageStart et agentId de la requête sont ignorés (Expand and Contract de l'API).
        reservation.setConditionBefore(conditionOf(vehicle));
        reservation.setMileageStart(vehicle.getMileageKM());
        reservation.setNotesBefore(request.notesBefore());
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setCreatedBy(currentUser);
        reservation.setUpdatedBy(currentUser);
        Reservation saved = saveOrConflict(reservation);
        notificationService.createForAllAdminsAndAgents(NotificationType.NEW_RESERVATION, saved);
        return ReservationResponse.from(saved);
    }

    @Transactional
    public ReservationResponse getReservationById(User currentUser, UUID id) {
        return ReservationResponse.from(findOne(currentUser, id));
    }

    private Reservation findOne(User currentUser, UUID id) {
        return findInScope(currentUser, id, ReservationScope.Action.READ);
    }

    // Écriture : la réservation est verrouillée AVANT d'être lue, pour décider sur son état à jour
    // (sinon une modification lue avant une annulation l'écraserait). Puis contrôle de la portée.
    private Reservation lockInScope(User currentUser, UUID id, ReservationScope.Action action) {
        reservationRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
        return findInScope(currentUser, id, action);
    }

    // Hors portée : même 404 qu'un id inexistant
    private Reservation findInScope(User currentUser, UUID id, ReservationScope.Action action) {
        Specification<Reservation> byId = (root, query, cb) -> cb.equal(root.get("id"), id);
        return reservationRepository.findOne(byId.and(reservationScope.allowedFor(currentUser, action)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    // Décision R3 : le DRIVER (portée own) modifie sa réservation avant son début seulement ;
    // l'AGENT et l'ADMIN (portée all) tant qu'elle n'est pas retournée, sans déplacer un début passé.
    @Transactional
    public ReservationResponse updateReservation(User currentUser, UUID id, ReservationUpdateRequest request) {
        Reservation reservation = lockInScope(currentUser, id, ReservationScope.Action.UPDATE);
        requireOpen(reservation);

        OffsetDateTime now = OffsetDateTime.now();
        boolean started = reservation.hasStartedAt(now);
        if (started && !reservationScope.coversAll(currentUser, ReservationScope.Action.UPDATE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Réservation commencée : seul un agent peut encore la modifier");
        }
        if (started && request.startDate() != null && !request.startDate().isEqual(reservation.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Réservation commencée : sa date de début ne peut plus changer");
        }

        OffsetDateTime start = request.startDate() != null ? request.startDate() : reservation.getStartDate();
        OffsetDateTime end = request.endDate() != null ? request.endDate() : reservation.getEndDate();
        if (!start.isEqual(reservation.getStartDate()) || !end.isEqual(reservation.getEndDate())) {
            validateNewPeriod(reservation, start, end, now);
            reservation.setStartDate(start);
            reservation.setEndDate(end);
        }
        if (request.notesBefore() != null) {
            reservation.setNotesBefore(request.notesBefore());
        }
        reservation.setUpdatedBy(currentUser);
        return ReservationResponse.from(saveOrConflict(reservation));
    }

    // Annulation possible avant le début seulement ; après, c'est un retour (décision R3)
    @Transactional
    public ReservationResponse cancelReservation(User currentUser, UUID id) {
        Reservation reservation = lockInScope(currentUser, id, ReservationScope.Action.CANCEL);
        requireOpen(reservation);

        OffsetDateTime now = OffsetDateTime.now();
        if (reservation.hasStartedAt(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Réservation commencée : elle ne peut plus être annulée, enregistrez son retour");
        }
        reservation.setCancelledAt(now);
        reservation.setCancelledBy(currentUser);
        reservation.setUpdatedBy(currentUser);
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        notificationService.replaceWithCancellationNotice(saved);
        return ReservationResponse.from(saved);
    }

    // Une réservation annulée ou retournée est figée
    private void requireOpen(Reservation reservation) {
        if (reservation.getCancelledAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Réservation annulée");
        }
        if (reservation.getReturnedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Réservation déjà retournée");
        }
    }

    // Mêmes règles qu'à la création, sans que la réservation se gêne elle-même
    private void validateNewPeriod(Reservation reservation, OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }
        if (!reservation.hasStartedAt(now) && start.isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be in the past");
        }
        if (!end.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate cannot be in the past");
        }
        // Verrou sur le véhicule, comme à la création : les demandes simultanées passent l'une après l'autre
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(reservation.getVehicle().getId())
            .filter(v -> v.getDeletedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Véhicule introuvable"));
        // Une location en cours peut être prolongée même si le véhicule est passé en maintenance entre-temps
        if (!reservation.hasStartedAt(now) && vehicle.getStatus() != Vehicle.VehicleStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Véhicule non disponible");
        }
        if (reservationRepository.hasConflict(vehicle.getId(), start, end, reservation.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, SLOT_TAKEN);
        }
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

        // Le statut stocké ne change pas (RESERVED est calculé) : un véhicule passé en maintenance y reste
        Vehicle vehicle = reservation.getVehicle();
        vehicle.setMileageKM(request.mileageEnd());
        // L'état constaté au retour devient l'état du véhicule (réutilisé par la réservation suivante)
        vehicle.setCondition(Vehicle.VehicleCondition.valueOf(request.conditionAfter().name()));

        return ReservationResponse.from(reservationRepository.saveAndFlush(reservation));
    }

    private Reservation findReservationOrThrow(UUID id) {
        return reservationRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
    }

    // Filet de sécurité : si une écriture contourne le verrou, la contrainte reservations_no_overlap (V14)
    // refuse le chevauchement ; la réponse est le même conflit.
    // 23P01 = violation d'une contrainte EXCLUDE (seule contrainte de ce type du schéma)
    private Reservation saveOrConflict(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, SLOT_TAKEN);
            }
            throw e;
        }
    }

    // Un champ de conducteur dans la requête : réservé à la création pour un tiers (AGENT, ADMIN)
    private boolean designatesADriver(ReservationRequest request) {
        return request.driverId() != null
            || request.driverFirstName() != null
            || request.driverLastName() != null
            || request.driverEmail() != null;
    }

    // Les deux enums ont les mêmes valeurs (EXCELLENT, GOOD, FAIR, POOR) : conversion par le nom
    private Reservation.Condition conditionOf(Vehicle vehicle) {
        if (vehicle.getCondition() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "État du véhicule inconnu : renseignez-le avant de le réserver");
        }
        return Reservation.Condition.valueOf(vehicle.getCondition().name());
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

        // Pas de conflit de réservation
        if (reservationRepository.hasConflict(vehicle.getId(), request.startDate(), request.endDate())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, SLOT_TAKEN);
        }
    }

    private void validateReturn(Reservation reservation, ReturnVehicleRequest request){
        if (reservation.getCancelledAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Réservation annulée");
        }

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
