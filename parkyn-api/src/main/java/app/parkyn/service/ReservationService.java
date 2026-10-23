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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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
        Set.of("ACTIVE", "UPCOMING", "IN_PROGRESS", "ON_HOLD", "RETURNED", "CANCELLED");

    private static final String SLOT_TAKEN =
        "Véhicule déjà réservé sur ce créneau : choisissez un autre véhicule ou une autre plage horaire";

    @Transactional
    public Page<ReservationResponse> getReservations(User currentUser, String status, UUID vehicleId, Pageable pageable) {
        if (status != null && !STATUS_FILTERS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu : " + status);
        }
        String filter = status;
        OffsetDateTime now = OffsetDateTime.now();
        Set<UUID> onHold = reservationRepository.findOnHoldIds(null, now);
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
                case "IN_PROGRESS" -> predicates.add(cb.and(cb.isNull(returnedAt), cb.isNull(cancelledAt),
                    cb.lessThanOrEqualTo(startDate, now), notIn(cb, root.get("id"), onHold)));
                case "ON_HOLD" -> predicates.add(in(cb, root.get("id"), onHold));
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
        return reservationRepository.findAll(spec, pageable)
            .map(reservation -> ReservationResponse.from(reservation, now, onHold.contains(reservation.getId())));
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
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse getReservationById(User currentUser, UUID id) {
        return toResponse(findOne(currentUser, id));
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
        // Le DRIVER est bloqué dès la date de début (R3), même si la réservation est en attente
        if (reservation.hasStartedAt(now) && !reservationScope.coversAll(currentUser, ReservationScope.Action.UPDATE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Réservation commencée : seul un agent peut encore la modifier");
        }
        // En attente, le véhicule n'a pas été remis : l'agent peut encore la reporter
        boolean inProgress = reservation.hasStartedAt(now) && !reservationRepository.isOnHold(id, now);
        if (inProgress && request.startDate() != null && !request.startDate().isEqual(reservation.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Réservation commencée : sa date de début ne peut plus changer");
        }

        OffsetDateTime start = request.startDate() != null ? request.startDate() : reservation.getStartDate();
        OffsetDateTime end = request.endDate() != null ? request.endDate() : reservation.getEndDate();
        if (!start.isEqual(reservation.getStartDate()) || !end.isEqual(reservation.getEndDate())) {
            validateNewPeriod(reservation, start, end, now, inProgress);
            reservation.setStartDate(start);
            reservation.setEndDate(end);
        }
        if (request.notesBefore() != null) {
            reservation.setNotesBefore(request.notesBefore());
        }
        reservation.setUpdatedBy(currentUser);
        return toResponse(saveOrConflict(reservation));
    }

    // Annulation possible avant le début seulement ; après, c'est un retour (décision R3)
    @Transactional
    public ReservationResponse cancelReservation(User currentUser, UUID id) {
        Reservation reservation = lockInScope(currentUser, id, ReservationScope.Action.CANCEL);
        requireOpen(reservation);

        OffsetDateTime now = OffsetDateTime.now();
        // En attente, le véhicule n'a pas été remis : l'agent peut encore l'annuler (le DRIVER non, R3)
        boolean agentCancelsOnHold = reservationScope.coversAll(currentUser, ReservationScope.Action.CANCEL)
            && reservationRepository.isOnHold(id, now);
        if (reservation.hasStartedAt(now) && !agentCancelsOnHold) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Réservation commencée : elle ne peut plus être annulée, enregistrez son retour");
        }
        reservation.setCancelledAt(now);
        reservation.setCancelledBy(currentUser);
        reservation.setUpdatedBy(currentUser);
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        notificationService.replaceWithCancellationNotice(saved);
        return toResponse(saved);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        OffsetDateTime now = OffsetDateTime.now();
        return ReservationResponse.from(reservation, now, reservationRepository.isOnHold(reservation.getId(), now));
    }

    // IN sur un ensemble vide : aucune ligne (et NOT IN : toutes), sans dépendre du SQL généré pour une liste vide
    private static Predicate in(CriteriaBuilder cb, Path<UUID> id, Set<UUID> ids) {
        return ids.isEmpty() ? cb.disjunction() : id.in(ids);
    }

    private static Predicate notIn(CriteriaBuilder cb, Path<UUID> id, Set<UUID> ids) {
        return ids.isEmpty() ? cb.conjunction() : cb.not(id.in(ids));
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
    private void validateNewPeriod(Reservation reservation, OffsetDateTime start, OffsetDateTime end,
                                   OffsetDateTime now, boolean inProgress) {
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }
        // Un nouveau début ne peut pas être dans le passé (un début déjà passé et inchangé reste valable)
        if (!start.isEqual(reservation.getStartDate()) && start.isBefore(now)) {
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
        if (!inProgress && vehicle.getStatus() != Vehicle.VehicleStatus.AVAILABLE) {
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

        return toResponse(reservationRepository.saveAndFlush(reservation));
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
        // Le véhicule n'a jamais été remis : un retour écraserait le kilométrage du véhicule encore dehors
        if (reservationRepository.isOnHold(reservation.getId(), OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Réservation en attente : le véhicule n'a pas été remis, annulez-la ou reportez-la");
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
