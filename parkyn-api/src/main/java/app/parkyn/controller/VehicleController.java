package app.parkyn.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import app.parkyn.dto.VehicleRequest;
import app.parkyn.dto.VehicleResponse;
import app.parkyn.dto.VehicleStatusRequest;
import app.parkyn.dto.VehicleUpdateRequest;
import app.parkyn.model.Vehicle;
import app.parkyn.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("")
    public ResponseEntity<Page<VehicleResponse>> getVehicles(
        @RequestParam(required = false) Vehicle.VehicleStatus status,
        @RequestParam(required = false) String search,
        @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok().body(vehicleService.getVehicles(status, search, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PostMapping("")
    public ResponseEntity<VehicleResponse> addVehicle(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse response = vehicleService.addVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getVehicleById(@PathVariable UUID id) {
        VehicleResponse response = vehicleService.getVehicleById(id);
        return ResponseEntity.ok().body(response);
    }

    @PreAuthorize("hasAuthority('vehicle:update')")
    @PatchMapping("/{id}")
    public ResponseEntity<VehicleResponse> updateVehicleById(@RequestBody VehicleUpdateRequest request, @PathVariable UUID id) {
        VehicleResponse response = vehicleService.updateVehicleById(id, request);
        return ResponseEntity.ok().body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicleById(@PathVariable UUID id) {
        vehicleService.deleteVehicleById(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('vehicle:status:update')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<VehicleResponse> updateVehicleStatus(@PathVariable UUID id, @Valid @RequestBody VehicleStatusRequest request) {
        VehicleResponse response = vehicleService.updateVehicleStatus(id, request);
        return ResponseEntity.ok().body(response);
    }
}
