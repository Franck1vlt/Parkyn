package app.parkyn.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.dto.VehicleRequest;
import app.parkyn.dto.VehicleResponse;
import app.parkyn.dto.VehicleStatusRequest;
import app.parkyn.dto.VehicleUpdateRequest;
import app.parkyn.model.Notification.NotificationType;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.ReservationRepository;
import app.parkyn.repository.VehicleRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;

    // from / to : uniquement les véhicules libres sur cette plage horaire (même règle que la réservation)
    @Transactional
    public Page<VehicleResponse> getVehicles(Vehicle.VehicleStatus status, String search,
                                             OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        if ((from == null) != (to == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from et to doivent être fournis ensemble");
        }
        if (from != null && !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from doit être avant to");
        }
        Set<UUID> inUse = reservationRepository.findVehicleIdsInUseAt(null, OffsetDateTime.now());
        Set<UUID> booked = from == null ? Set.of() : reservationRepository.findVehicleIdsBookedBetween(null, null, from, to);

        Specification<Vehicle> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            // AVAILABLE et RESERVED sont calculés : véhicule disponible, dehors ou non à cet instant.
            // Avec une plage horaire, AVAILABLE veut dire « libre sur la plage » : l'usage actuel ne compte pas
            if (status == Vehicle.VehicleStatus.AVAILABLE && from != null) {
                predicates.add(cb.equal(root.get("status"), Vehicle.VehicleStatus.AVAILABLE));
            } else if (status == Vehicle.VehicleStatus.AVAILABLE) {
                predicates.add(cb.equal(root.get("status"), Vehicle.VehicleStatus.AVAILABLE));
                predicates.add(notIn(cb, root.get("id"), inUse));
            } else if (status == Vehicle.VehicleStatus.RESERVED) {
                predicates.add(cb.equal(root.get("status"), Vehicle.VehicleStatus.AVAILABLE));
                predicates.add(in(cb, root.get("id"), inUse));
            } else if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.equal(root.get("status"), Vehicle.VehicleStatus.AVAILABLE));
                predicates.add(notIn(cb, root.get("id"), booked));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("brand")), pattern),
                    cb.like(cb.lower(root.get("model")), pattern),
                    cb.like(cb.lower(root.get("plateNumber")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return vehicleRepository.findAll(spec, pageable)
            .map(vehicle -> VehicleResponse.from(vehicle, inUse.contains(vehicle.getId())));
    }


    @Transactional
    public VehicleResponse addVehicle(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(request.plateNumber());
        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        vehicle.setYear(request.year());
        vehicle.setMileageKM(request.mileageKM());
        vehicle.setCondition(request.condition());
        vehicle.setCommissionedAt(request.commissionedAt());
        return toResponse(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public VehicleResponse getVehicleById(UUID id) {
        return toResponse(findActiveVehicle(id));
    }

    @Transactional
    public VehicleResponse updateVehicleById(UUID id, VehicleUpdateRequest request) {
        Vehicle vehicle = findActiveVehicle(id);
        if (request.plateNumber() != null)    vehicle.setPlateNumber(request.plateNumber());
        if (request.brand() != null)          vehicle.setBrand(request.brand());
        if (request.model() != null)          vehicle.setModel(request.model());
        if (request.year() != null)           vehicle.setYear(request.year());
        if (request.mileageKM() != null)      vehicle.setMileageKM(request.mileageKM());
        if (request.condition() != null)      vehicle.setCondition(request.condition());
        if (request.commissionedAt() != null) vehicle.setCommissionedAt(request.commissionedAt());
        return toResponse(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public void deleteVehicleById(UUID id) {
        Vehicle vehicle = findActiveVehicle(id);
        vehicle.setDeletedAt(OffsetDateTime.now());
        vehicleRepository.saveAndFlush(vehicle);
    }

    @Transactional
    public VehicleResponse updateVehicleStatus(UUID id, VehicleStatusRequest request) {
        Vehicle vehicle = findActiveVehicle(id);
        Vehicle.VehicleStatus current = vehicle.getStatus();
        Vehicle.VehicleStatus next = request.status();

        if (next == Vehicle.VehicleStatus.RESERVED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                "Le statut RESERVED est calculé à partir des réservations, il ne se modifie pas à la main");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                "Transition " + current + " → " + next + " interdite");
        }

        vehicle.setStatus(request.status());

        if (Vehicle.VehicleStatus.MAINTENANCE == request.status()) {
                    notificationService.createForAllAdminsAndAgents(
            NotificationType.VEHICLE_MAINTENANCE, null);
        }
        return toResponse(vehicleRepository.save(vehicle));
    }

    private static final Map<Vehicle.VehicleStatus, Set<Vehicle.VehicleStatus>> ALLOWED_TRANSITIONS = Map.of(
        Vehicle.VehicleStatus.AVAILABLE,    Set.of(Vehicle.VehicleStatus.MAINTENANCE),
        Vehicle.VehicleStatus.MAINTENANCE,  Set.of(Vehicle.VehicleStatus.AVAILABLE, Vehicle.VehicleStatus.RETIRED),
        Vehicle.VehicleStatus.RETIRED,      Set.of()
    );

    private VehicleResponse toResponse(Vehicle vehicle) {
        boolean inUse = !reservationRepository.findVehicleIdsInUseAt(vehicle.getId(), OffsetDateTime.now()).isEmpty();
        return VehicleResponse.from(vehicle, inUse);
    }

    // IN sur un ensemble vide : aucune ligne (et NOT IN : toutes), sans dépendre du SQL généré pour une liste vide
    private static Predicate in(CriteriaBuilder cb, Path<UUID> id, Set<UUID> ids) {
        return ids.isEmpty() ? cb.disjunction() : id.in(ids);
    }

    private static Predicate notIn(CriteriaBuilder cb, Path<UUID> id, Set<UUID> ids) {
        return ids.isEmpty() ? cb.conjunction() : cb.not(id.in(ids));
    }

    private Vehicle findActiveVehicle(UUID id){
        return vehicleRepository.findById(id).filter(v -> v.getDeletedAt() == null).orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Vehicle not found"));
    }
}
