package app.parkyn.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.dto.VehicleRequest;
import app.parkyn.dto.VehicleResponse;
import app.parkyn.dto.VehicleStatusRequest;
import app.parkyn.dto.VehicleUpdateRequest;
import app.parkyn.model.Vehicle;
import app.parkyn.repository.VehicleRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    @Transactional
    public Page<VehicleResponse> getVehicles(Vehicle.VehicleStatus status, Pageable pageable) {
        Specification<Vehicle> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return vehicleRepository.findAll(spec, pageable).map(VehicleResponse::from);
    }

    @Transactional
    public VehicleResponse addVehicle(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNumber(request.plateNumber());
        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        vehicle.setYear(request.year());
        vehicle.setMileageKM(request.mileageKM());
        vehicle.setCommissionedAt(request.commissionedAt());
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public VehicleResponse getVehicleById(UUID id) {
        return VehicleResponse.from(findActiveVehicle(id));
    }

    @Transactional
    public VehicleResponse updateVehicleById(UUID id, VehicleUpdateRequest request) {
        Vehicle vehicle = findActiveVehicle(id);
        if (request.plateNumber() != null)    vehicle.setPlateNumber(request.plateNumber());
        if (request.brand() != null)          vehicle.setBrand(request.brand());
        if (request.model() != null)          vehicle.setModel(request.model());
        if (request.year() != null)           vehicle.setYear(request.year());
        if (request.mileageKM() != null)      vehicle.setMileageKM(request.mileageKM());
        if (request.commissionedAt() != null) vehicle.setCommissionedAt(request.commissionedAt());
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
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

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                "Transition " + current + " → " + next + " interdite");
        }

        vehicle.setStatus(request.status());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private static final Map<Vehicle.VehicleStatus, Set<Vehicle.VehicleStatus>> ALLOWED_TRANSITIONS = Map.of(
        Vehicle.VehicleStatus.AVAILABLE,    Set.of(Vehicle.VehicleStatus.MAINTENANCE, Vehicle.VehicleStatus.RESERVED),
        Vehicle.VehicleStatus.RESERVED,     Set.of(Vehicle.VehicleStatus.AVAILABLE),
        Vehicle.VehicleStatus.MAINTENANCE,  Set.of(Vehicle.VehicleStatus.AVAILABLE, Vehicle.VehicleStatus.RETIRED),
        Vehicle.VehicleStatus.RETIRED,      Set.of()
    );

    private Vehicle findActiveVehicle(UUID id){
        return vehicleRepository.findById(id).filter(v -> v.getDeletedAt() == null).orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Vehicle not found"));
    }
}
